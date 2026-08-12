package com.AIstudy.delichat.upload.service;

import com.AIstudy.delichat.common.exception.ApplicationException;
import com.AIstudy.delichat.upload.dto.UploadCompleteResponse;
import com.AIstudy.delichat.upload.dto.UploadInitResponse;
import com.AIstudy.delichat.upload.dto.UploadSessionResult;
import com.AIstudy.delichat.upload.repository.UploadSessionRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadSessionService {

    private static final String BUCKET_NAME = "chat-images";
    private static final int PRESIGNED_URL_EXPIRY_MINUTES = 10;
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final MinioClient minioClient;
    private final UploadSessionRepository uploadSessionRepository;

    // 1. 최종 객체명을 미리 확정하고 세션을 저장한 뒤, 그 객체에 대한 PUT presigned URL을 바로 발급한다.
    public UploadInitResponse init(String filename, String contentType) {
        String objectName = generateObjectName(filename);
        Long sessionId = uploadSessionRepository.save(objectName, filename, contentType);

        try {
            String uploadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .expiry(PRESIGNED_URL_EXPIRY_MINUTES, TimeUnit.MINUTES)
                            .build()
            );
            return new UploadInitResponse(sessionId, uploadUrl);
        } catch (Exception e) {
            log.error("[업로드 URL 발급 실패] sessionId={}, error={}", sessionId, e.getMessage());
            throw new ApplicationException("업로드 URL 발급에 실패했습니다: sessionId=" + sessionId, e);
        }
    }

    // 2. 클라이언트가 uploadUrl로 파일을 다 올린 뒤 호출한다. 객체가 실제로 존재하는지 확인하고 세션을 완료 처리한다.
    public UploadCompleteResponse complete(Long sessionId) {
        UploadSessionResult session = getSessionOrThrow(sessionId);

        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(session.objectName())
                            .build()
            );
        } catch (Exception e) {
            log.error("[업로드 확인 실패] sessionId={}, objectName={}, error={}", sessionId, session.objectName(), e.getMessage());
            throw new ApplicationException("업로드된 객체를 확인할 수 없습니다: sessionId=" + sessionId, e);
        }

        uploadSessionRepository.updateStatus(sessionId, STATUS_COMPLETED);

        return new UploadCompleteResponse(getDownloadUrl(session.objectName()));
    }

    private String getDownloadUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .expiry(PRESIGNED_URL_EXPIRY_MINUTES, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("[다운로드 URL 발급 실패] objectName={}, error={}", objectName, e.getMessage());
            throw new ApplicationException("다운로드 URL 발급에 실패했습니다: " + objectName, e);
        }
    }

    private UploadSessionResult getSessionOrThrow(Long sessionId) {
        return uploadSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.NOT_FOUND, "업로드 세션을 찾을 수 없습니다: " + sessionId, null));
    }

    private String generateObjectName(String filename) {
        return UUID.randomUUID() + extractExtension(filename);
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}