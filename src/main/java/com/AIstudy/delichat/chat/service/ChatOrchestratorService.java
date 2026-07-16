package com.AIstudy.delichat.chat.service;

import com.AIstudy.delichat.chat.dto.ChatMessageResult;
import com.AIstudy.delichat.chat.repository.ChatMessageRepository;
import com.AIstudy.delichat.chat.repository.ChatSessionRepository;
import com.AIstudy.delichat.chat.repository.RagEvalLogRepository;
import com.AIstudy.delichat.rag.dto.FaqContextResult;
import com.AIstudy.delichat.rag.service.FaqSearchService;
import com.AIstudy.delichat.rag.service.QueryRewriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatOrchestratorService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final QueryRewriteService queryRewriteService;
    private final FaqSearchService faqSearchService;
    private final ChatAnswerService chatAnswerService;
    private final RagEvalLogRepository ragEvalLogRepository;

    public Flux<String> handle(Long sessionId, String userQuestion){

        List<ChatMessageResult> history = chatMessageRepository.findRecentMessage(sessionId);

        chatMessageRepository.save(sessionId,"user",userQuestion);

        String rewrittenQuestion = queryRewriteService.rewrite(history,userQuestion);

        FaqContextResult faqContext = faqSearchService.buildContextFor(rewrittenQuestion);
        Long memberId = chatSessionRepository.findMemberId(sessionId);

        StringBuilder fullAnswer = new StringBuilder();
        return chatAnswerService.answerStream(rewrittenQuestion,faqContext.context(),memberId)
                .doOnNext(fullAnswer::append)
                .doOnComplete(()->{
                    String answer = fullAnswer.toString();
                    chatMessageRepository.save(sessionId,"assistant",answer);

                    if(faqContext.found()){
                        ragEvalLogRepository.save(sessionId,rewrittenQuestion,faqContext.context(),answer);
                    }

                });
    }
}
