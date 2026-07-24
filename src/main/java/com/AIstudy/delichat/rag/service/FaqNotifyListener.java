package com.AIstudy.delichat.rag.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*

 cs_faq에 새 행이 삽입되면 Postgres가 발행하는 'faq_inserted' NOTIFY를
 전용 커넥션으로 LISTEN하고 있다가 즉시 임베딩을 채운다.
 폴링 대신 push 방식을 쓰는 이유: 새 FAQ 반영을 최대한 실시간에 가깝게 하기 위함.
*/
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqNotifyListener {

    private static final String CHANNEL = "faq_inserted";
    private static final int NOTIFICATION_TIMEOUT_MS = 30_000;
    private static final long INITIAL_RECONNECT_DELAY_MS = 5_000;
    private static final long MAX_RECONNECT_DELAY_MS = 60_000;
    private final FaqEmbeddingService faqEmbeddingService;
    private long reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running = true;
    private volatile Connection connection;

    // 첫 시작 시 놓쳤던 문서들 임베딩
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        executor.submit(this::listenLoop);
    }

    // 종료 시 리소스 정리
    @PreDestroy
    public void stop() {
        running = false;
        closeQuietly();
        executor.shutdownNow();
    }

    private void listenLoop() {
        while (running) {
            try {
                connect();
                reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;

                // 1. 최초 시작 시점, 그리고 재연결 직후(끊겨있던 동안 놓친 알림 보정) 놓쳤던 문서 백필
                int backfilled = faqEmbeddingService.embeddingMissingFaqs();
                log.info("FAQ 임베딩 백필 완료: {}건", backfilled);

                // 2. 어플이 실행되고 있는 경우
                while (running) {
                    // 3. 최대 30초 동안 NOTIFY 알림이 오는지 대기
                    PGNotification[] notifications = connection.unwrap(PGConnection.class)
                            .getNotifications(NOTIFICATION_TIMEOUT_MS);
                    // 4. 알림이 오면 임베딩 처리
                    if (notifications != null) {
                        for (PGNotification notification : notifications) {
                            handleNotification(notification);
                        }
                    }
                }
            } catch (Exception e) {
                if (running) {
                    // 5. DB 연결이 끊기면 지수 백오프만큼 대기 후 재연결, 다음 실패를 대비해 지연을 2배로 늘림
                    log.error("FAQ NOTIFY 리스너 연결이 끊어졌습니다. {}ms 후 재연결합니다.", reconnectDelayMs, e);
                    sleepQuietly(reconnectDelayMs);
                    reconnectDelayMs = Math.min(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS);
                }
            } finally {
                closeQuietly();
            }
        }
    }

    // LISTEN은 오래 살아있는 전용 커넥션이 필요해서 DriverManager로 별도 커넥션 생성
    private void connect() throws SQLException {
        connection = DriverManager.getConnection(jdbcUrl, username, password);
        connection.setAutoCommit(true);
        try (Statement statement = connection.createStatement()) {
            statement.execute("LISTEN " + CHANNEL);
        }
        log.info("FAQ NOTIFY 리스너 연결됨 (channel={})", CHANNEL);
    }

    private void handleNotification(PGNotification notification) {
        try {
            Long id = Long.valueOf(notification.getParameter());
            if (faqEmbeddingService.tryClaimAndEmbed(id)) {
                log.info("FAQ id={} 임베딩 완료 (NOTIFY)", id);
            }
        } catch (Exception e) {
            log.error("FAQ NOTIFY 처리 실패: payload={}", notification.getParameter(), e);
        }
    }

    private void closeQuietly() {
        Connection conn = connection;
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}