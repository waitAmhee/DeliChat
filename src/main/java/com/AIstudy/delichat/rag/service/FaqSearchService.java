package com.AIstudy.delichat.rag.service;


import com.AIstudy.delichat.rag.dto.FaqContextResult;
import com.AIstudy.delichat.rag.repository.FaqRepository;
import com.AIstudy.delichat.rag.dto.FaqSimilarResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FaqSearchService {

    //TEST -> 임계값 테스트 대상
    private static final double SIMILARITY_THRESHOLD= 0.5;
    private static final int TOP_K=3;

    private final FaqRepository faqRepository;
    private final EmbeddingService embeddingService;
    private final ChatModel chatModel;

    /**
     *
     * 스트리밍 버전. 토큰 단위로 응답이 흘러나오는 Flux를 반환한다.
     * 근거자료가 없으면 찾지 못함 메시지를 담은 Flux하나만 emit하고 끝냄
     */
    public FaqContextResult buildContextFor(String userQuestion){
        float[] queryVector = embeddingService.embed(userQuestion);
        List<FaqSimilarResult> results = faqRepository.searchSimilarFaqs(queryVector,TOP_K);

        List<FaqSimilarResult> relevant = results.stream()
                .filter(r-> r.similarity()>=SIMILARITY_THRESHOLD)
                .toList();

        if(relevant.isEmpty()){
            return new FaqContextResult("",false);
        }

        return new FaqContextResult(buildContext(relevant),true);
    }

    public Flux<String> answerStreamWithContext(String userQuestion, String context){
        if(context.isEmpty()){
            return Flux.just("죄송해요, 지금 이 질문에는 답변을 드리기 어려워요. 다른 방식으로 여쭤봐 주시겠어요?");
        }

        Prompt prompt = buildPrompt(userQuestion,context);

        return chatModel.stream(prompt)
                .filter(response -> response.getResult() != null)
                .map(response -> response.getResult().getOutput().getText())
                .filter(text-> text!=null && !text.isEmpty());
    }

    private String buildContext(List<FaqSimilarResult> results){
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<results.size();i++){
            FaqSimilarResult r = results.get(i);
            sb.append("[참고자료 ").append(i+1).append("]\n")
                    .append("Q: ").append(r.question()).append("\n")
                    .append("A: ").append(r.answer()).append("\n\n");
        }
        return sb.toString();
    }

    private Prompt  buildPrompt(String userQuestion, String context){
        //TEST -> 프롬프트 테스트 예정
        String promptText= """
                너는 배달 서비스의 고객센터 챗봇입니다.
                아래 참고자료만 근거로 답변하고, 참고자료에 없는 내용은 지어내지 마라.
                참고자료로 답할 수 없으면 모른다고 솔직히 말해라.
                친절하고 간결한 존댓말로 답변해라.      
                
                각 문장 끝에는 그 문장의 근거가 된 참고 자료 번호를 [1], [2] 형태로 표시해라.
                여러 참고자료를 함께 사용했다면 [1][2]처럼 모두 표시해라.
                참고자료 없이 일반 상식으로 답한 문장에는 표시하지 마라.
                
                %s
                사용자 질문: %s
                """.formatted(context,userQuestion);

        return new Prompt(promptText);

    }


}
