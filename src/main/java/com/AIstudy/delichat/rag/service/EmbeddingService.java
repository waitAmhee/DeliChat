package com.AIstudy.delichat.rag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmbeddingService {
    private final EmbeddingModel embeddingModel;

    public float[] embed(String text){
        return embeddingModel.embed(text);
    }

    // float[] -> 문자열 형태로 변환 -> ::vector로 캐스팅 -> PostgreSQL의 vector타입으로 저장.
    public String toVectorLiteral(float[] vec){
        StringBuilder sb = new StringBuilder("[");
        for(int i=0;i<vec.length;i++){
            sb.append(vec[i]);
            if(i<vec.length-1) sb.append(",");
        }
        return sb.append("]").toString();
    }


}
