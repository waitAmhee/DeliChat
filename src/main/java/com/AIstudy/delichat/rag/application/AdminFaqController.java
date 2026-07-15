package com.AIstudy.delichat.rag.application;

import com.AIstudy.delichat.rag.service.FaqEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@RequiredArgsConstructor
public class AdminFaqController {
    private final FaqEmbeddingService faqEmbeddingService;

    @PostMapping("/admin/faq/embed")
    public ResponseEntity<Map<String,Object>> embedMissingFaqs(){
        int count = faqEmbeddingService.embeddingMissingFaqs();
        return ResponseEntity.ok(
                Map.of("embeddedCount",count,
                        "status","completed")
        );
    }
}
