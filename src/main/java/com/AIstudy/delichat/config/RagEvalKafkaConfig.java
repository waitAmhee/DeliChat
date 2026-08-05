package com.AIstudy.delichat.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/*
 judge/FAQ 개선 후보 추출 컨슈머가 공용으로 쓰는 에러 처리 설정 + 토픽 정의.
 일시 오류는 짧게 재시도하고, 재시도를 다 소진하면 "<토픽>.DLT"로 보낸다.
 소비자마다 별도 토픽을 파지 않고, 토픽 2개(raw/judged)에 컨슈머 그룹만 늘리는 방식으로 인프라를 최소화했다.
*/
@Configuration
public class RagEvalKafkaConfig {

    private static final long RETRY_INTERVAL_MS = 2_000L;
    private static final long MAX_RETRY_ATTEMPTS = 3L;
    private static final int PARTITION_COUNT = 15;

    // KafkaAdmin이 부트 시점에 이 빈을 읽어 브로커에 토픽을 생성 --> AdminClient.createTopics() 호출
    // 토픽을 스펙으로 관리 (인프라를 코드화)
    @Bean
    public NewTopic ragEvalRequestedTopic(
            @Value("${app.kafka.topic.rag-eval-requested}") String topicName,
            @Value("${app.kafka.replication-factor}") short replicationFactor,
            @Value("${app.kafka.min-insync-replicas}") String minInsyncReplicas) {
        return TopicBuilder.name(topicName)
                .partitions(PARTITION_COUNT)
                .replicas(replicationFactor) // 각 파티션을 몇개의 브로커에 복제할지
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInsyncReplicas) // 최소 ISR 개수. acks=all로 발행할 때 최소 몇개의 복제본에 써져야 성공으로 간주할지
                .build();
    }

    // judge가 채점을 마치면 이 토픽에 재발행하고, FAQ 개선 후보 추출 컨슈머가 구독한다 (이벤트 체이닝).
    @Bean
    public NewTopic ragEvalJudgedTopic(
            @Value("${app.kafka.topic.rag-eval-judged}") String topicName,
            @Value("${app.kafka.replication-factor}") short replicationFactor,
            @Value("${app.kafka.min-insync-replicas}") String minInsyncReplicas) {
        return TopicBuilder.name(topicName)
                .partitions(PARTITION_COUNT)
                .replicas(replicationFactor)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInsyncReplicas)
                .build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> ragEvalKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${app.kafka.consumer-concurrency}") int concurrency) {
        // 1. @KafkaListener는 내부적으로 리스너 컨테이너가 백그라운드 스레드에서 poll()을 반복 실행하며 컨슈머 메서드들이 동작함
        //    -> 그 리스너 컨테이너를 만드는 factory
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        factory.setConcurrency(concurrency);

        // 2. 메시지를 처리하다가 예외 발생 시 -> .DLT 토픽으로 옮겨져서 처리될 때 사용되는 객체
        //TODO 일시적 오류 vs 영구 오류 차이 구현
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate); // DLT로 재발행하는 것도 카프카에 메시지를 보내는 것 = 발행 도구(kafkaTemplate) 필요
        factory.setCommonErrorHandler(
                new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRY_ATTEMPTS))); // 재시도 실패 시 DLT, 재시도 간격/횟수
        return factory;
    }
}
