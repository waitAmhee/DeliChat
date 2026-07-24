package com.AIstudy.delichat.chat.service;

import com.AIstudy.delichat.chat.dto.ChatMessageResult;
import com.AIstudy.delichat.chat.repository.ChatMessageRepository;
import com.AIstudy.delichat.chat.repository.ChatSessionRepository;
import com.AIstudy.delichat.chat.repository.RagEvalLogRepository;
import com.AIstudy.delichat.rag.dto.FaqSearchOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

// 채팅 요청 전체 흐름을 조율하는 곳
@Service
@RequiredArgsConstructor
public class ChatOrchestratorService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatAnswerService chatAnswerService;
    private final RagEvalLogRepository ragEvalLogRepository;

    public Flux<String> handle(Long sessionId, String userQuestion){

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
                .doOnComplete(()->{
                    String answer = fullAnswer.toString();
                    // 5. 스트림이 끝나면 완성된 assistant 답변을 대화 기록에 저장
                    chatMessageRepository.save(sessionId,"assistant",answer);

                    // 6. FAQ 검색 도구가 실제 참고 자료를 찾은 경우, 나중에 LLM-as-a-judge로 평가
                    FaqSearchOutcome faqOutcome = faqOutcomeHolder.get();
                    if(faqOutcome != null && faqOutcome.result().found()){
                        ragEvalLogRepository.save(sessionId,faqOutcome.query(),faqOutcome.result().context(),answer);
                    }

                });
    }
}
