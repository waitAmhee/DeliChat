package com.AIstudy.delichat.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaqEmbeddingService {

    private final JdbcTemplate jdbcTemplate;

    // Spring AI를 통해 임베딩 모델에게 토큰화 + 벡터 변환을 요청하는 추상화 계층.
    private final EmbeddingService embeddingService;

    // embedding이 비어있는 행을 모두 훑어 클레임 시도. 앱 시작 시점 백필과
    // NOTIFY 리스너의 재연결 보정에서 재사용된다.
    public int embeddingMissingFaqs() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM cs_faq WHERE embedding IS NULL", Long.class
        );

        int embedded = 0;
        for (Long id : ids) {
            // 한 행이 계속 실패해도(임베딩 API 오류, 토큰 초과 등) 나머지 행은 계속 처리되어야 한다.
            // 여기서 잡지 않으면 예외가 루프 밖(FaqNotifyListener의 연결 관리 catch)까지 전파되어
            // DB 연결 문제로 오인되고, 이후 행들이 영영 임베딩되지 않는다.
            try {
                if (tryClaimAndEmbed(id)) {
                    embedded++;
                }
            } catch (Exception e) {
                log.error("FAQ id={} 임베딩 실패, 다음 행으로 계속 진행합니다.", id, e);
            }
        }
        return embedded;
    }

    // FOR UPDATE SKIP LOCKED로 행을 클레임한 뒤 임베딩을 채운다.
    // (멀티 인스턴스 환경에서 같은 행을 중복으로 OpenAI에 임베딩 요청하는 것을 방지).
    public boolean tryClaimAndEmbed(Long id) {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) conn -> {
            conn.setAutoCommit(false);

            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT question, answer " +
                            "FROM cs_faq " +
                            "WHERE id = ? AND embedding IS NULL" +
                            " FOR UPDATE SKIP LOCKED")) {
                select.setLong(1, id);
                try (ResultSet rs = select.executeQuery()) {

                    // 잠금 획득 실패
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }

                    String text = rs.getString("question") + " " + rs.getString("answer");
                    float[] vector = embeddingService.embed(text);
                    String literal = embeddingService.toVectorLiteral(vector);

                    try (PreparedStatement update = conn.prepareStatement(
                            "UPDATE cs_faq SET embedding = ?::vector WHERE id = ?")) {
                        update.setString(1, literal);
                        update.setLong(2, id);
                        update.executeUpdate();
                    }

                    conn.commit();
                    return true;
                }
            } finally {
                conn.setAutoCommit(true);
            }
        }));
    }
}