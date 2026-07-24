package com.AIstudy.delichat.rag.service;

import com.AIstudy.delichat.rag.dto.MultiCriteriaJudgement;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class RagEvalJudgeService {

    private static final String JUDGE_MODEL="gpt-4o";

    private final ChatModel chatModel;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;
    private final ExecutorService virtualThreadExecutor;

    // 미채점 로그가 많을 때 judge 모델 호출이 한꺼번에 몰리지 않도록
    // 설정값만큼만 동시에 평가 작업 실행
    public RagEvalJudgeService(ChatModel chatModel, JdbcTemplate jdbcTemplate, JsonMapper jsonMapper,
                                @Value("${app.judge.max-concurrent-judges}") int maxConcurrentJudges) {
        this.chatModel = chatModel;
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.virtualThreadExecutor = Executors.newFixedThreadPool(maxConcurrentJudges, Thread.ofVirtual().factory());
    }

    private static final String JUDGE_PROMPT = """
        너는 배달 서비스 CS 챗봇의 답변 품질을 엄격하게 평가하는 심사자다.
    아래 질문, 근거자료, 답변을 보고 3가지 기준으로 각각 1~5점을 매겨라.

    1. faithfulness: 답변의 모든 문장이 근거자료로 실제 뒷받침되는가.
       근거자료에 없는 내용을 지어냈다면 낮게 평가해라.
    2. relevancy: 답변이 사용자의 질문에 실제로 답하고 있는가.
    3. tone: 배달 서비스 CS 챗봇다운 친절하고 간결한 존댓말을 지켰는가.

    faithfulness가 4점 미만이라면, 그 원인을 다음 중 하나로 분류해라:
    - "insufficient_context": 근거자료 자체에 질문에 답할 정보가 부족해서 답변이 불완전한 경우
    - "hallucination": 근거자료에 없는 내용을 답변이 사실인 것처럼 지어낸 경우
    faithfulness가 4점 이상이면 failure_type은 "none"으로 표시해라.

    질문: %s
    근거자료: %s
    답변: %s

    아래 JSON 형식으로만 응답해라. 다른 텍스트는 절대 포함하지 마라.
    {"faithfulness": 숫자, "relevancy": 숫자, "tone": 숫자, "failure_type": "none 또는 insufficient_context 또는 hallucination", "reason": "종합 평가 근거 한두 문장"}
    """;

    // 아직 채점 결과가 없는 RAG 평가 로그를 조회해 병렬로 채점
    public int judgeUnjudgedLogs(){
        // 1. judgement 테이블에 결과가 없는 평가 로그만 조회
        List<Map<String, Object>> unjudgedLogs = jdbcTemplate.queryForList(
                """
                        SELECT rel.id, rel.question, rel.context, rel.answer
                                FROM rag_eval_log rel
                                LEFT JOIN rag_eval_judgement rej ON rel.id = rej.eval_log_id
                                WHERE rej.id IS NULL
                    """
        );

        // 2. 각 로그를 가상 스레드에 제출해 LLM judge 채점을 병렬 실행
        List<Future<Boolean>> futures = unjudgedLogs.stream()
                .map(row -> virtualThreadExecutor.submit(() -> judgeAndSave(row)))
                .toList();

        // 3. 각 작업의 완료 결과를 기다리고, 성공한 채점 건수만 집계
        int judgedCount = 0;
        for (Future<Boolean> future : futures) {
            try {
                if (Boolean.TRUE.equals(future.get())) {
                    judgedCount++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                System.out.println("[JUDGE 실패] " + e.getCause());
            }
        }

        // 4. 이번 실행에서 실제로 채점 저장에 성공한 로그 수를 반환한다.
        return judgedCount;
    }


    private boolean judgeAndSave(Map<String, Object> row){
        // 1.1. 조회된 row에서 judge prompt에 필요한 컬럼 값을 꺼낸다.
        Long evalLogId = ((Number) row.get("id")).longValue();
        String question = (String) row.get("question");
        String context = (String) row.get("context");
        String answer = (String) row.get("answer");

        try {
            // 2. LLM-as-a-Judge로 답변 품질을 채점하고 결과를 DB에 저장한다.
            MultiCriteriaJudgement judgement = judge(question, context, answer);
            save(evalLogId, judgement);

            // 3. 근거 없는 답변으로 판정된 경우 운영 알림/후속 조치 대상
            if ("hallucination".equals(judgement.failureType())) {
                //TODO 여기서 알림 전송이나 다른 방식으로 처리.
                System.out.println("[HALLUCINATION 발견] eval_log_id=" + evalLogId + ", reason=" + judgement.reason());
            }
            return true;
        } catch (Exception e) {
            System.out.println("[JUDGE 실패] eval_log_id=" + evalLogId + ", reason=" + e.getMessage());
            return false;
        }
    }

    private MultiCriteriaJudgement judge(String question, String context, String answer){
        // 1. 질문, 검색 근거, 실제 답변을 judge prompt에 넣어 평가 요청을 만든다.
        String promptText = JUDGE_PROMPT.formatted(question,context,answer);
        Prompt prompt = new Prompt(promptText,
                OpenAiChatOptions.builder().model(JUDGE_MODEL).build()
                );
        // 2. judge 모델을 동기 호출해 JSON 형식의 채점 결과를 받음
        String rawResponse = chatModel.call(prompt)
                .getResult().getOutput().getText()
                .trim();

        // 3. 받은 결과를 DTO와 파싱
        try{
            return jsonMapper.readValue(rawResponse,MultiCriteriaJudgement.class);
        }catch (Exception e){
            throw new RuntimeException("Judge 응답 파싱 실패: " + rawResponse, e);
        }
    }

    // LLM judge 결과를 평가 로그 id와 연결해 저장
    private void save(Long evalLogId,MultiCriteriaJudgement j){
        jdbcTemplate.update("""
                        INSERT INTO rag_eval_judgement
                        (eval_log_id, faithfulness_score, relevancy_score, tone_score, failure_type, reason)
                        VALUES (?, ?, ?, ?, ?, ?)
        """,evalLogId,j.faithfulness(),j.relevancy(),j.tone(),j.failureType(),j.reason());
    }
}
