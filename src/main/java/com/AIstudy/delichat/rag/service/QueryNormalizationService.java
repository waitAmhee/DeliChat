package com.AIstudy.delichat.rag.service;

import kr.co.shineware.nlp.komoran.constant.DEFAULT_MODEL;
import kr.co.shineware.nlp.komoran.core.Komoran;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/*
 KOMORAN으로 조사/어미/문장부호를 제거하고 의미를 담은 형태소만 남겨 캐시 키를 정규화한다.
    KOMORAN은 한국어 형태소 분석기 라이브러리 -> 문장을 형태소(의미의 최소 단위)로 쪼개고 각 형태소에 품사 태그를 붙여줌.
*/
@Slf4j
@Service
public class QueryNormalizationService {

    // 조사(J*)·어미(E*)·문장부호(S*)는 제외하고 의미를 담은 품사만 남긴다
    // komoran 라이브러리의 품사 태그
    private static final String[] CONTENT_POS_TAGS = {
            "NNG", "NNP", "NNB", "NP", "NR", // 명사류
            "VV", "VA", "VX", "XR",          // 용언 어간/어근
            "MAG",                            // 부사
            "SL", "SH", "SN"                  // 외국어/한자/숫자
    };

    private final Komoran komoran = new Komoran(DEFAULT_MODEL.LIGHT);

    // 사용자의 질문을 받으면 정규화 시키는 메서드
    public String normalize(String question){
        if (question == null || question.isBlank()) {
            return "";
        }

        try {
            // 1. 형태소 분석 후 지정된 품사에 해당하는 형태소만 리스트로 추출
            List<String> morphemes = komoran.analyze(question).getMorphesByTags(CONTENT_POS_TAGS);

            // 2. 형태소가 하나도 안 남았으면(전부 필터링된 경우) 원문으로 폴백
            if (morphemes.isEmpty()) {
                return question.trim();
            }

            // 3. 공백으로 이어붙여 정규화된 캐시 키 문자열 생성
            return String.join(" ", morphemes).trim();
        } catch (Exception e) {
            log.warn("질문 정규화 실패, 원문을 그대로 사용합니다. question={}", question, e);
            return question.trim();
        }
    }
}