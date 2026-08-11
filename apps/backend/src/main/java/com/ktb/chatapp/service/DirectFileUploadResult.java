package com.ktb.chatapp.service;

import com.ktb.chatapp.model.File;
import java.net.URI;
import lombok.Builder;
import lombok.Data;

/** Presigned PUT URL 발급 결과. local 스토리지는 directUpload=false를 반환한다. */
@Data
@Builder
public class DirectFileUploadResult {
    private boolean directUpload;
    private URI uploadUrl;
    private File file;
}
