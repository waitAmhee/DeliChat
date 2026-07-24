package com.AIstudy.delichat.rag.service;

import com.AIstudy.delichat.chat.dto.ChatMessageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QueryRewriteService {

    private final ChatModel chatModel;

    // 대화 이력을 참고해 후속 질문을 RAG 검색에 사용할 수 있는 독립 질문으로 재작성
    public String rewrite(List<ChatMessageResult> history, String newQuestion){

        if(history.isEmpty()){
            return newQuestion;
        }

        String historyText = history.stream()
                .map(m->m.role() +": "+m.content())
                .collect(Collectors.joining("\n"));

        String promptText = """
                   아래는 사용자와 챗봇의 대화 이력과, 사용자의 새 질문이다.
                   새 질문이 대화 이력 없이도 그 자체로 이해 가능하도록,
                   생략된 주제나 대상을 명시해서 독립적인 질문 하나로 다시 써라.
                   원래 의미를 벗어나지 말고, 새 질문에 없는 내용을 추가하지 마라.
                   재작성된 질문만 출력하고 다른 설명은 붙이지 마라.
                   
                   대화이력:
                   %s
                   
                   새 질문: %s
                   
                   재작성된 질문:
                """.formatted(historyText,newQuestion);

        // 동기 호출 -> 보통 재작성 결과를 이용해서 FAQ 검색
        return chatModel.call(new Prompt(promptText))
                .getResult().getOutput().getText()
                .trim();
    }
}
