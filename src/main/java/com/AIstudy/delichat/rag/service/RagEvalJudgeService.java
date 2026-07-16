package com.AIstudy.delichat.rag.service;

import com.AIstudy.delichat.rag.dto.MultiCriteriaJudgement;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RagEvalJudgeService {

    private static final String JUDGE_MODEL="gpt-4o";

    private final ChatModel chatModel;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    public int judgeUnjudgedLogs(){
        List<Map<String, Object>> unjudgedLogs = jdbcTemplate.queryForList(
                """
                        SELECT rel.id, rel.question, rel.context, rel.answer
                                FROM rag_eval_log rel
                                LEFT JOIN rag_eval_judgement rej ON rel.id = rej.eval_log_id
                                WHERE rej.id IS NULL
                    """
        );

        for(Map<String,Object> row:unjudgedLogs){
            Long evalLogId = ((Number) row.get("id")).longValue();
            String question = (String) row.get("question");
            String context = (String) row.get("context");
            String answer = (String) row.get("answer");

            MultiCriteriaJudgement judgement = judge(question, context, answer);
            save(evalLogId,judgement);

            if("hallucination".equals(judgement.failureType())){
                //TODO 여기서 알림 전송이나 다른 방식으로 처리.
                System.out.println("[HALLUCINATION 발견] eval_log_id=" + evalLogId + ", reason=" + judgement.reason());
            }
        }

        return unjudgedLogs.size();
    }

    private MultiCriteriaJudgement judge(String question, String context, String answer){
        String prompText = JUDGE_PROMPT.formatted(question,context,answer);
        Prompt prompt = new Prompt(prompText,
                OpenAiChatOptions.builder().model(JUDGE_MODEL).build()
                );
        String rawResponse = chatModel.call(prompt)
                .getResult().getOutput().getText()
                .trim();

        try{
            return objectMapper.readValue(rawResponse,MultiCriteriaJudgement.class);
        }catch (Exception e){
            throw new RuntimeException("Judge 응답 파싱 실패: " + rawResponse, e);
        }
    }

    private void save(Long evalLogId,MultiCriteriaJudgement j){
        jdbcTemplate.update("""
                        INSERT INTO rag_eval_judgement
                        (eval_log_id, faithfulness_score, relevancy_score, tone_score, failure_type, reason)
                        VALUES (?, ?, ?, ?, ?)
        """,evalLogId,j.faithfulness(),j.relevancy(),j.tone(),j.failureType(),j.reason());
    }
}
