package com.AIstudy.delichat.chat.service;

import com.AIstudy.delichat.chat.dto.ChatMessageResult;
import com.AIstudy.delichat.chat.repository.ChatMessageRepository;
import com.AIstudy.delichat.chat.repository.ChatSessionRepository;
import com.AIstudy.delichat.image.ChatVisionService;
import com.AIstudy.delichat.rag.dto.FaqSearchOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

// 채팅 요청 전체 흐름을 조율하는 곳
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatOrchestratorService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatAnswerService chatAnswerService;
    private final RagEvalPersistenceService ragEvalPersistenceService;
    private final ChatVisionService chatVisionService;

    public Flux<String> handle(Long sessionId, String userQuestion,List<String> imageUrls){

        // 이미지를 캡션으로 만들기
        if(imageUrls != null && !imageUrls.isEmpty()){
            String caption = chatVisionService.generateCaption(imageUrls);
            userQuestion = userQuestion + "\n[첨부 이미지 설명] "+caption;
        }

        // 1. 해당 세션의 최근 메시지 조회
        List<ChatMessageResult> history = chatMessageRepository.findRecentMessage(sessionId);

        // 2. 사용자의 새 질문을 먼저 저장해 대화 기록을 남김
        chatMessageRepository.save(sessionId,"user",userQuestion);

        // 3. tool 호출에 필요한 사용자 식별자와 FAQ 검색 결과 holder를 준비
        Long memberId = chatSessionRepository.findMemberId(sessionId);
        // FAQ 검색 tool이 찾은 query/context를 스트림 완료 후 RAG 평가 로그로 남기기 위해서
        AtomicReference<FaqSearchOutcome> faqOutcomeHolder = new AtomicReference<>();

        // 4. 스트리밍으로 조각난 AI 답변을 클라이언트에게 흘려보내면서 전체 답변 누적
        StringBuilder fullAnswer = new StringBuilder();
        return chatAnswerService.answer(history,userQuestion,memberId,faqOutcomeHolder)
                .doOnNext(fullAnswer::append)
                .doOnError(this::logIfRateLimited)
                .doOnComplete(()->{
                    String answer = fullAnswer.toString();
                    // 5. 스트림이 끝나면 완성된 assistant 답변을 대화 기록에 저장
                    chatMessageRepository.save(sessionId,"assistant",answer);

                    // 6. rag_eval_log 저장(found일 때만) + outbox 이벤트 발행(항상)을 한 트랜잭션으로 처리
                    FaqSearchOutcome faqOutcome = faqOutcomeHolder.get();
                    ragEvalPersistenceService.recordEvalOutcome(sessionId, faqOutcome, answer);
                });
    }

    // 429 등 재시도 가능한 OpenAI 오류(TransientAiException)만 별도 태그로 눈에 띄게 로그. 원
    private void logIfRateLimited(Throwable e){
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof TransientAiException) {
                log.warn("[RATE_LIMIT 발견] stage-1 답변 생성 중 OpenAI 호출이 재시도 후에도 실패했습니다: {}", cause.getMessage());
                return;
            }
        }
    }
}
