package com.AIstudy.delichat.config;

import com.AIstudy.delichat.order.service.OrderQueryTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ChatClientConfig {

    private final ChatClient.Builder chatClientBuilder;
    private final OrderQueryTools orderQueryTools;

    @Bean
    public ChatClient toolCallingChatClient() {
        return chatClientBuilder.defaultTools(orderQueryTools).build();
    }
}