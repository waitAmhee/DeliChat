package com.AIstudy.delichat.upload.dto;

public record UploadSessionResult(Long id, String objectName, String originalFilename,
                                   String contentType, String status) {
}