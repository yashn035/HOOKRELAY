package com.hookrelay.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Service
@Profile("aws")
@ConditionalOnProperty(name = "aws.enabled", havingValue = "true")
public class S3ArchivalService {

    private final S3Client s3Client;
    private final String bucketName;
    private static final DateTimeFormatter DATE_PARTITION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);

    public S3ArchivalService(
            S3Client s3Client,
            @Value("${aws.s3.bucket:hookrelay-delivery-logs}") String bucketName
    ) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public String archiveDeliveryLog(UUID deliveryId, String logContent, Instant timestamp) {
        if (deliveryId == null || logContent == null) {
            throw new IllegalArgumentException("deliveryId and logContent cannot be null");
        }
        Instant effectiveTime = timestamp != null ? timestamp : Instant.now();
        String datePrefix = DATE_PARTITION_FORMATTER.format(effectiveTime);
        String s3Key = String.format("%s/%s.json.gz", datePrefix, deliveryId);

        try {
            byte[] gzippedBytes = compressGzip(logContent);

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .contentEncoding("gzip")
                    .contentType("application/json")
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(gzippedBytes));
            log.info("Successfully archived delivery {} to s3://{}/{} ({} bytes compressed)",
                    deliveryId, bucketName, s3Key, gzippedBytes.length);
            return s3Key;
        } catch (Exception ex) {
            log.error("Failed to archive delivery log {} to S3 bucket {}: {}", deliveryId, bucketName, ex.getMessage(), ex);
            throw new RuntimeException("S3 archival failed: " + ex.getMessage(), ex);
        }
    }

    public byte[] compressGzip(String data) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
            gzipStream.write(data.getBytes(StandardCharsets.UTF_8));
        }
        return byteStream.toByteArray();
    }

    public String decompressGzip(byte[] compressed) throws IOException {
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(gzipInputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String getBucketName() {
        return bucketName;
    }
}

