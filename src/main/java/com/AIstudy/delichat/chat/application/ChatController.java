package com.AIstudy.delichat.chat.application;

import com.AIstudy.delichat.chat.service.ChatOrchestratorService;
import com.AIstudy.delichat.chat.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatOrchestratorService chatOrchestratorService;
    private final ChatSessionService chatSessionService;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PostMapping("/chat/session")
    public ResponseEntity<Map<String,Long>> createSession(@RequestParam(required = false) Long memberId){
        Long sessionId = chatSessionService.createSession(memberId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("sessionId", sessionId));
    }

    @GetMapping(value="/chat/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnswer(@RequestParam Long sessionId,@RequestParam String question){
        SseEmitter emitter = new SseEmitter(60_000L);

        //DOC 버츄얼 스레드로 변경 이유 정리
        virtualThreadExecutor.submit(()->{
            chatOrchestratorService.handle(sessionId,question)
                    .subscribe(
                            token->{
                                try{
                                    emitter.send(token);
                                }catch (Exception e){
                                    emitter.completeWithError(e);
                                }
                            },
                            emitter::completeWithError,
                            emitter::complete
                    );
        });
        return emitter;
    }
}
