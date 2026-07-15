package com.AIstudy.delichat.chat.service;

import com.AIstudy.delichat.chat.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatSessionService {
    private final ChatSessionRepository chatSessionRepository;

    public Long createSession() {
        return chatSessionRepository.createSession();
    }
}
