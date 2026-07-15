package com.AIstudy.delichat;

import com.AIstudy.delichat.rag.service.EmbeddingService;
import com.AIstudy.delichat.rag.repository.FaqRepository;
import com.AIstudy.delichat.rag.dto.FaqSimilarResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class FaqRepositoryTest {

    @Autowired
    FaqRepository faqRepository;

    @Autowired
    EmbeddingService embeddingService;

    @Test
    void checkSimilarityScores(){
        List<String> testQuestions = List.of(
                "환불 어떻게 해요",                  // FAQ에 있는 표현과 가까움
                "환불 신청하고 싶은데 방법 알려줘",     // 표현은 다르지만 의도는 동일
                "오늘 날씨 어때",                    // 완전히 무관한 질문
                "배달이 너무 늦어요",
                "배달 현황을 확인하고 싶어요",
                "주문 취소하고 싶어요",
                "점심 뭐 먹지",
                "지하철 몇시에 끊겨",
                "리뷰 삭제하고 싶어요"                // 다른 카테고리지만 유사한 패턴의 질문
        );

        for(String question: testQuestions){
            float[] queryVector = embeddingService.embed(question);
            List<FaqSimilarResult> results = faqRepository.searchSimilarFaqs(queryVector,3);

            System.out.println("질문: "+question);
            for(FaqSimilarResult r: results){
                System.out.printf(" [%.3f] (%s) %s%n", r.similarity(), r.category(), r.question());
            }
            System.out.println();
        }

    }
}
