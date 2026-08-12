package com.AIstudy.delichat.upload.application;

import com.AIstudy.delichat.upload.dto.UploadCompleteResponse;
import com.AIstudy.delichat.upload.dto.UploadInitRequest;
import com.AIstudy.delichat.upload.dto.UploadInitResponse;
import com.AIstudy.delichat.upload.service.UploadSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadSessionService uploadSessionService;

    @PostMapping("/init")
    public UploadInitResponse init(@RequestBody UploadInitRequest request) {
        return uploadSessionService.init(request.filename(), request.contentType());
    }

    @PostMapping("/{sessionId}/complete")
    public UploadCompleteResponse complete(@PathVariable Long sessionId) {
        return uploadSessionService.complete(sessionId);
    }
}