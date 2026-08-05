package com.AIstudy.delichat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.concurrent.Executors;

/*
 임베딩 호출(FAQ 검색)과 judge의 동기 호출은 RestClient를 쓴다.
 커스텀 빈이 없으면 JDK HttpClient가 내부 비동기 처리에 기본 executor(제한된 풀)를 쓰는데, 동시 요청이 몰리면
 이 executor에서도 WebClient 쪽과 비슷하게 요청이 밀릴 수 있어 -> 버추얼 스레드 executor로 교체한다
*/
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        HttpClient httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient));
    }
}