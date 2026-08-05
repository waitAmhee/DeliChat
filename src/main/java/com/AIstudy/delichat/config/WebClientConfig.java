package com.AIstudy.delichat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/*
 Spring AI가 OpenAI 스트리밍 호출에 쓰는 WebClient는 커스텀 빈이 없으면 Reactor Netty 기본 커넥션(maxConnections = 2 x CPU 코어 수)을 그대로 사용.
 채팅 한 턴마다 재질문 판단/FAQ 검색 임베딩 + 답변 생성으로 OpenAI를 여러 번 호출하는데, 동시 요청이 몰리면 기본 풀 크기로는 커넥션을 기다리며 요청이 순차적으로 밀리는 현상 발생
 OpenAiChatAutoConfiguration/OpenAiEmbeddingAutoConfiguration은 이 WebClient.Builder 빈이 있으면  기본값 대신 그대로 가져다 쓴다.
*/
@Configuration
public class WebClientConfig {

    private static final int MAX_CONNECTIONS = 200;

    @Bean
    public WebClient.Builder webClientBuilder() {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("openai-webclient-pool")
                .maxConnections(MAX_CONNECTIONS)
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}