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

@Service
@RequiredArgsConstructor
public class ChatOrchestratorService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatAnswerService chatAnswerService;
    private final RagEvalLogRepository ragEvalLogRepository;

    public Flux<String> handle(Long sessionId, String userQuestion){

        List<ChatMessageResult> history = chatMessageRepository.findRecentMessage(sessionId);

        chatMessageRepository.save(sessionId,"user",userQuestion);

        Long memberId = chatSessionRepository.findMemberId(sessionId);
        AtomicReference<FaqSearchOutcome> faqOutcomeHolder = new AtomicReference<>();

        StringBuilder fullAnswer = new StringBuilder();
        return chatAnswerService.answer(history,userQuestion,memberId,faqOutcomeHolder)
                .doOnNext(fullAnswer::append)
                .doOnComplete(()->{
                    String answer = fullAnswer.toString();
                    chatMessageRepository.save(sessionId,"assistant",answer);

                    FaqSearchOutcome faqOutcome = faqOutcomeHolder.get();
                    if(faqOutcome != null && faqOutcome.result().found()){
                        ragEvalLogRepository.save(sessionId,faqOutcome.query(),faqOutcome.result().context(),answer);
                    }

                });
    }
}
