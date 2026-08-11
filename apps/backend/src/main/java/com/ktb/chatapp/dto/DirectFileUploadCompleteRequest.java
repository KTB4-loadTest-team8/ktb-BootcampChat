package com.ktb.chatapp.dto;

import jakarta.validation.constraints.NotBlank;

/** 브라우저의 S3 PUT 성공 후 파일 메타데이터를 확정한다. */
public record DirectFileUploadCompleteRequest(@NotBlank String fileId) {}
