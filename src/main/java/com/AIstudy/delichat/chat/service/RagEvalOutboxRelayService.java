package com.AIstudy.delichat.chat.service;

import com.AIstudy.delichat.chat.repository.RagEvalOutboxRepository;
import com.AIstudy.delichat.rag.dto.RagEvalRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/*
    outbox에 쌓인 행을 Kafka RAG_EVAL_REQUESTED 토픽으로 발행하는 relay 로직.
    FOR UPDATE SKIP LOCKED
*/
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEvalOutboxRelayService {

    // 재시도를 이 횟수만큼 소진하면 outbox 행을 FAILED로 종결한다 (무한 재시도 방지)
    private static final int MAX_PUBLISH_ATTEMPTS = 5;

    private final RagEvalOutboxRepository ragEvalOutboxRepository;
    // Boot가 자동구성하는 KafkaTemplate 빈은 <Object, Object>로 등록됨
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Value("${app.kafka.topic.rag-eval-requested}")
    private String ragEvalRequestedTopic;

    // 리스너 시작/재연결 시 놓쳤던 PENDING 행들을 훑어 발행. RagEvalOutboxRelay의 백필 보정에서 재사용된다.
    public int publishPendingBacklog(){
        List<Long> pendingIds = ragEvalOutboxRepository.findPendingIds();
        return pendingIds.isEmpty() ? 0 : tryClaimAndPublishBatch(pendingIds);
    }

    // 클레임 + 논블로킹 발행
    @Transactional
    public int tryClaimAndPublishBatch(List<Long> ids){
        Map<Long, CompletableFuture<SendResult<Object, Object>>> sendFutures = new LinkedHashMap<>();

        // 1. 각 id를 클레임 (FOR UPDATE SKIP LOCKED) 하고 논블로킹으로 발행
        //    세션 id를 같은 파티션 키로 둬서 같은 세션의 이벤트 순서를 보존. -> 다른 relay 인스턴스가 이미 잡은 id는 건너뜀
        for (Long id : ids) {
            ragEvalOutboxRepository.claimForPublish(id).ifPresent(row -> {
                RagEvalRequestedEvent event = new RagEvalRequestedEvent(
                        row.id(), row.sessionId(), row.question(), row.context(), row.answer(),
                        row.found(), row.evalLogId()
                );
                sendFutures.put(id, kafkaTemplate.send(ragEvalRequestedTopic, String.valueOf(row.sessionId()), event));
            });
        }

        // 2. 배치 전체 완료를 한 번만 대기
        CompletableFuture.allOf(sendFutures.values().toArray(CompletableFuture[]::new))
                .exceptionally(ignored -> null) // n건이 실팰하더라도 무시하고 넘어감
                .join();

        // 3. 결과를 id별로 확인해서 성공/실패 반영
        int published = 0;
        for (Map.Entry<Long, CompletableFuture<SendResult<Object, Object>>> entry : sendFutures.entrySet()) {
            try {
                entry.getValue().join();
                ragEvalOutboxRepository.markPublished(entry.getKey());
                published++;
            } catch (Exception e) {
                ragEvalOutboxRepository.markFailed(entry.getKey(), e.getMessage(), MAX_PUBLISH_ATTEMPTS);
            }
        }
        return published;
    }
}
