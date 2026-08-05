package com.AIstudy.delichat.chat.service;

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
import java.util.ArrayList;
import java.util.List;

/*

 rag_eval_outbox에 새 행이 삽입되면 Postgres가 발행하는 'rag_eval_outbox_inserted' NOTIFY를
 전용 커넥션으로 LISTEN하고 있다가 즉시 Kafka RAG_EVAL_REQUESTED 토픽으로 발행한다.

*/
@Slf4j
@Component
@RequiredArgsConstructor
public class RagEvalOutboxRelay {

    private static final String CHANNEL = "rag_eval_outbox_inserted";
    private static final int NOTIFICATION_TIMEOUT_MS = 30_000;
    private static final long INITIAL_RECONNECT_DELAY_MS = 5_000;
    private static final long MAX_RECONNECT_DELAY_MS = 60_000;
    private final RagEvalOutboxRelayService ragEvalOutboxRelayService;
    private long reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    // 앱 생명주기 내내 하나만 떠 있는 전용 리스너라 가상 스레드 풀 대신 일반 플랫폼 스레드 하나로 충분
    private Thread listenerThread;
    private volatile boolean running = true;
    private volatile Connection connection;

    // 시작 시점에 놓쳤던 발행 대상들을 백필
    // ApplicationReadyEvent -> 스프링 컨텍스트가 완전히 뜬 뒤 발생
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        listenerThread = Thread.ofPlatform().name("rag-eval-outbox-listener").start(this::listenLoop);
    }

    // 종료 시 리소스 정리
    @PreDestroy
    public void stop() {
        running = false;
        closeQuietly();
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }

    private void listenLoop() {
        // 연결이 끊기면 재연결
        while (running) {
            try {
                connect();
                reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;

                // 1. 최초 시작 시점, 그리고 재연결 직후(끊겨있던 동안 놓친 알림 보정) 놓쳤던 행 백필
                int backfilled = ragEvalOutboxRelayService.publishPendingBacklog();
                log.info("RAG 평가 outbox 백필 완료: {}건", backfilled);

                // 2. 어플이 실행되고 있는 경우 (실제 리스닝 루프)
                while (running) {
                    // 3. 최대 30초 동안 NOTIFY 알림이 오는지 대기
                    PGNotification[] notifications = connection.unwrap(PGConnection.class)
                            .getNotifications(NOTIFICATION_TIMEOUT_MS);
                    // 4. 알림이 오면 배치로 모아서 한 번에 발행 처리 (건별 처리하면 프로듀서가 배치를 못 묶음)
                    if (notifications != null && notifications.length > 0) {
                        handleNotifications(notifications);
                    }
                }
            } catch (Exception e) {
                if (running) {
                    // 5. DB 연결이 끊기면 지수 백오프만큼 대기 후 재연결, 다음 실패를 대비해 지연을 2배로 늘림
                    log.error("RAG 평가 outbox 리스너 연결이 끊어졌습니다. {}ms 후 재연결합니다.", reconnectDelayMs, e);
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
        log.info("RAG 평가 outbox 리스너 연결됨 (channel={})", CHANNEL);
    }

    private void handleNotifications(PGNotification[] notifications) {
        List<Long> ids = new ArrayList<>();
        for (PGNotification notification : notifications) {
            try {
                ids.add(Long.valueOf(notification.getParameter()));
            } catch (NumberFormatException e) { // 파싱 실패한 개별 건은 로그만 남기고 건너뛰어서 배치 전체가 죽지 않도록 함
                log.error("RAG 평가 outbox NOTIFY payload 파싱 실패: {}", notification.getParameter(), e);
            }
        }

        if (ids.isEmpty()) {
            return;
        }

        try {
            int published = ragEvalOutboxRelayService.tryClaimAndPublishBatch(ids);
            log.info("outbox {}건 발행 완료 (NOTIFY 배치, 수신 {}건)", published, ids.size());
        } catch (Exception e) {
            log.error("RAG 평가 outbox NOTIFY 배치 발행 실패: ids={}", ids, e);
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
