package com.AIstudy.delichat.rag.service;

import com.AIstudy.delichat.rag.dto.RagEvalJudgedEvent;
import com.AIstudy.delichat.rag.repository.FaqImprovementCandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/*
    RAG_EVAL_JUDGED(judge가 실제로 채점을 마친 것만)를 구독해 FAQ 개선 후보를 추출한다.
    faithfulness_score/failure_type은 judge가 채점을 끝내야만 존재하는 정보라서 raw 토픽이 아닌 judge 결과 토픽을 구독
*/
@Service
@RequiredArgsConstructor
public class FaqImprovementCandidateConsumer {

    private static final String NO_FAILURE = "none";

    private final FaqImprovementCandidateRepository faqImprovementCandidateRepository;

    // judge 결과가 또 다른 이벤트가 돼서 다음 소비자로 이어지는 구조 -> topicId -> RAG_EVAL_JUDGED
    @KafkaListener(
            topics = "${app.kafka.topic.rag-eval-judged}",
            groupId = "rag-eval-faq-candidate",
            containerFactory = "ragEvalKafkaListenerContainerFactory")
    public void onRagEvalJudged(RagEvalJudgedEvent event){

        // 1. failure_type이 'none'이면 개선 후보가 아니므로 저장하지 않는다.
        if (NO_FAILURE.equals(event.failureType())) {
            return;
        }

        // 2. 개선 후보 db에 저장
        faqImprovementCandidateRepository.save(
                event.outboxId(), event.faithfulness(), event.failureType(), event.reason());
    }
}
