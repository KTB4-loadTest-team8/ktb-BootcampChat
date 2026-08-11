package com.ktb.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** 파일 바이트 없이 Presigned PUT URL을 요청할 때 사용하는 메타데이터다. */
public record DirectFileUploadRequest(
        @NotBlank String filename,
        @NotBlank String contentType,
        @Positive long size) {}
