package com.AIstudy.delichat.image;

import com.AIstudy.delichat.common.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatVisionService {

    private final ChatModel chatModel;

    private static final String CAPTION_PROMPT =
            "다음 이미지(들)을 배달 음식 CS 상담 관점에서 설명해줘. " +
                    "음식 상태, 포장 상태, 이상 여부를 중심으로 간결하게 한글로 요약해줘.";

    public String generateCaption(List<String> imageUrls) {
        List<Media> mediaList = imageUrls.stream()
                .map(this::toMedia)
                .toList();

        UserMessage userMessage = UserMessage.builder()
                .text(CAPTION_PROMPT)
                .media(mediaList)
                .build();

        try {
            return chatModel.call(new Prompt(userMessage))
                    .getResult()
                    .getOutput()
                    .getText();
        } catch (Exception e) {
            log.error("[VISION 캡션 생성 실패] error={}", e.getMessage());
            throw new ApplicationException("이미지 캡션 생성에 실패했습니다.", e);
        }
    }

    private Media toMedia(String imageUrl) {
        return Media.builder()
                .mimeType(guessMimeType(imageUrl))
                .data(URI.create(imageUrl))
                .build();
    }

    private MimeType guessMimeType(String imageUrl) {
        String path = URI.create(imageUrl).getPath();
        String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "png" -> MimeTypeUtils.IMAGE_PNG;
            case "gif" -> MimeTypeUtils.IMAGE_GIF;
            case "webp" -> MimeTypeUtils.parseMimeType("image/webp");
            default -> MimeTypeUtils.IMAGE_JPEG;
        };
    }
}