package com.AIstudy.delichat.rag.application;

import com.AIstudy.delichat.rag.service.RagEvalJudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class RagEvalJudgeController {

    private final RagEvalJudgeService ragEvalJudgeService;

    // TODO 미채점 로그를 채점하는 수동 controller로 구현
    @PostMapping("/admin/eval/judge")
    public ResponseEntity<Map<String, Integer>> judgeUnjudgedLogs() {
        int count = ragEvalJudgeService.judgeUnjudgedLogs();
        return ResponseEntity.ok(Map.of("judgedCount", count));
    }
}