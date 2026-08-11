package com.ktb.chatapp.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;

public interface StoragePort {
    StoredObject put(InputStream content, String key, String contentType, long size);
    Optional<Resource> open(String key);
    void delete(String key);

    /**
     * 브라우저가 스토리지로 파일 바이트를 직접 전송할 수 있는 짧은 수명의 PUT URL을 발급한다.
     * 로컬 스토리지는 이 전송 방식을 지원하지 않으므로 기본 구현은 비어 있는 값을 반환한다.
     */
    default Optional<URI> directUploadUrl(String key, String contentType, long size, Duration ttl) {
        return Optional.empty();
    }

    /**
     * Presigned PUT 완료 후 메타데이터를 확정하기 전에 실물이 존재하는지 확인한다.
     * 로컬 스토리지의 기본 구현은 기존 읽기 경로를 재사용한다.
     */
    default boolean exists(String key) {
        return open(key).isPresent();
    }

    /**
     * 오프로딩 확장 지점. 지원하지 않으면 앱이 바이트를 중계한다.
     *
     * <p>{@code disposition}은 앱이 직접 서빙할 때 붙이는 것과 같은 헤더다. 오프로딩된 응답에도 실어야
     * 다운로드가 조용히 미리보기로 바뀌지 않는다.
     */
    default Optional<URI> offloadUrl(String key, Duration ttl, ContentDisposition disposition) {
        return Optional.empty();
    }
}
