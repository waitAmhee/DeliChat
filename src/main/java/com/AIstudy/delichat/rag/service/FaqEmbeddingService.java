package com.AIstudy.delichat.rag.service;

import com.AIstudy.delichat.rag.dto.FaqQuestionAnswer;
import com.AIstudy.delichat.rag.repository.FaqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaqEmbeddingService {

    private final FaqRepository faqRepository;

    // Spring AI를 통해 임베딩 모델에게 토큰화 + 벡터 변환을 요청하는 추상화 계층.
    private final EmbeddingService embeddingService;

    // embedding이 비어있는 행을 모두 훑어 클레임 시도. 앱 시작 시점 백필과
    // NOTIFY 리스너의 재연결 보정에서 재사용된다.
    public int embeddingMissingFaqs() {
        List<Long> ids = faqRepository.findIdsMissingEmbedding();

        int embedded = 0;
        for (Long id : ids) {
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

    //
    @Transactional
    public boolean tryClaimAndEmbed(Long id) {
        // 1. faq 문서 select
        Optional<FaqQuestionAnswer> claimed = faqRepository.claimForEmbedding(id);
        if (claimed.isEmpty()) {
            return false;
        }

        // 2. 들고온 문서 임베딩 + 문자열 형태로 변환
        // TODO 해당 임베딩 과정 중에서도 DB 락을 잡고 있음 - 개선 사항
        FaqQuestionAnswer row = claimed.get();
        float[] vector = embeddingService.embed(row.question() + " " + row.answer());
        String literal = embeddingService.toVectorLiteral(vector);

        // 3. 문서 DB에 반영
        faqRepository.updateEmbedding(id, literal);
        return true;
    }
}
