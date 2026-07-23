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

    // TODO 인증 체계 부재로 memberId를 검증 없이 그대로 신뢰함 (IDOR 위험).
    // 이 값은 이후 OrderQueryTools의 tool-calling 조회에 그대로 쓰이므로,
    // 실제 로그인/인증 시스템이 붙기 전까지는 임의의 memberId로 타인의 주문을 조회할 수 있다.
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
            try {
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

            } catch (Exception e) {
                // handle()이 Flux를 만들기 전(구독 이전) 동기 구간에서 던지는 예외
                // subscribe의 에러 콜백을 절대 타지 않으므로 여기서 별도로 잡기
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
