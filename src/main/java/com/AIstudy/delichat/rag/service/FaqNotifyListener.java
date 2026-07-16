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

// cs_faq에 새 행이 삽입되면 Postgres가 발행하는 'faq_inserted' NOTIFY를
// 전용 커넥션으로 LISTEN하고 있다가 즉시 임베딩을 채운다.
// 폴링 대신 push 방식을 쓰는 이유: 새 FAQ 반영을 최대한 실시간에 가깝게 하기 위함.
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqNotifyListener {

    private static final String CHANNEL = "faq_inserted";
    private static final int NOTIFICATION_TIMEOUT_MS = 30_000;
    private static final long RECONNECT_DELAY_MS = 5_000;

    private final FaqEmbeddingService faqEmbeddingService;

    // HikariCP 풀 커넥션은 maxLifetime/validation으로 장시간 블로킹 대기에
    // 부적합해서, 커넥션 풀과 별개로 전용 커넥션을 하나 열어 유지한다.
    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running = true;
    private volatile Connection connection;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        executor.submit(this::listenLoop);
    }

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
                // 최초 시작 시점, 그리고 재연결 직후(끊겨있던 동안 놓친 알림 보정)
                // 놓친 행이 있는지 한 번씩 백필한다.
                int backfilled = faqEmbeddingService.embeddingMissingFaqs();
                log.info("FAQ 임베딩 백필 완료: {}건", backfilled);

                while (running) {
                    PGNotification[] notifications = connection.unwrap(PGConnection.class)
                            .getNotifications(NOTIFICATION_TIMEOUT_MS);
                    if (notifications != null) {
                        for (PGNotification notification : notifications) {
                            handleNotification(notification);
                        }
                    }
                }
            } catch (Exception e) {
                if (running) {
                    log.error("FAQ NOTIFY 리스너 연결이 끊어졌습니다. {}ms 후 재연결합니다.", RECONNECT_DELAY_MS, e);
                    sleepQuietly(RECONNECT_DELAY_MS);
                }
            } finally {
                closeQuietly();
            }
        }
    }

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