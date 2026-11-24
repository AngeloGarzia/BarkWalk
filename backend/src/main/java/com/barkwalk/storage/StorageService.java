package com.barkwalk.storage;

import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StorageService {
    private final String bucket;
    private final int signedUrlMinutes;

    public StorageService(
            @Value("${barkwalk.s3.bucket}") String bucket,
            @Value("${barkwalk.s3.signed-url-minutes:15}") int signedUrlMinutes) {
        this.bucket = bucket;
        this.signedUrlMinutes = signedUrlMinutes;
    }

    public String createObjectKey(String folder, String filename) {
        return folder + "/" + UUID.randomUUID() + "-" + filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /** Dev-friendly signed URL stub (MinIO/S3 wiring can replace this). */
    public String signedGetUrl(String storageKey) {
        return "http://localhost:9000/" + bucket + "/" + storageKey + "?ttl=" + Duration.ofMinutes(signedUrlMinutes).toMinutes();
    }
}
