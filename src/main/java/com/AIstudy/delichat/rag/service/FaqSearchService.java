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

    /**
     *
     * 스트리밍 버전. 토큰 단위로 응답이 흘러나오는 Flux를 반환한다.
     * 근거자료가 없으면 찾지 못함 메시지를 담은 Flux하나만 emit하고 끝냄
     */
    public FaqContextResult buildContextFor(String userQuestion){
        // 1. 사용자 질문 임베딩화
        float[] queryVector = embeddingService.embed(userQuestion);

        // 2. 사용자 질문과 유사한 3개 문서 select
        List<FaqSimilarResult> results = faqRepository.searchSimilarFaqs(queryVector,TOP_K);

        // 3. 유사도가 0.5 이상인 문서들로만 구성
        List<FaqSimilarResult> relevant = results.stream()
                .filter(r-> r.similarity()>=SIMILARITY_THRESHOLD)
                .toList();

        // 4. 없으면 context 전달 X
        if(relevant.isEmpty()){
            return new FaqContextResult("",false);
        }

        return new FaqContextResult(buildContext(relevant),true);
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

}
