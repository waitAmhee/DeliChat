package com.AIstudy.delichat.chat.service;

import com.AIstudy.delichat.chat.repository.RagEvalLogRepository;
import com.AIstudy.delichat.chat.repository.RagEvalOutboxRepository;
import com.AIstudy.delichat.rag.dto.FaqSearchOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 rag_eval_log 저장(found일 때만)과 outbox 이벤트 발행(found 여부 무관 항상)을
 같은 트랜잭션으로 묶는다 - outbox 패턴의 핵심 전제(DB 저장과 이벤트 발행의 원자성)를 만족시키기 위함.
*/
@Service
@RequiredArgsConstructor
public class RagEvalPersistenceService {

    private final RagEvalLogRepository ragEvalLogRepository;
    private final RagEvalOutboxRepository ragEvalOutboxRepository;

    @Transactional
    public void recordEvalOutcome(Long sessionId, FaqSearchOutcome faqOutcome, String answer){
        if (faqOutcome == null) {
            return;
        }

        // 1. FAQ 검색이 성공한 경우에만 rag_eval_log에 남긴다. 생성된 id는 judge 컨슈머가
        //    rag_eval_judgement와 연결하는 데 쓰인다.
        boolean found = faqOutcome.result().found();
        Long evalLogId = null;
        if (found) {
            evalLogId = ragEvalLogRepository.save(sessionId, faqOutcome.query(), faqOutcome.result().context(), answer);
        }

        // 2. outbox 이벤트는 found 여부와 무관하게 항상 발행한다 -
        //    검색 실패 케이스도 FAQ 개선 후보 추출에 가장 가치 있는 신호이기 때문
        ragEvalOutboxRepository.save(sessionId, faqOutcome.query(), faqOutcome.result().context(), answer, found, evalLogId);
    }
}
