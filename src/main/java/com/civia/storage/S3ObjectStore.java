package com.civia.storage;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Archivos en un almacenamiento compatible con S3. Con Cloudflare R2 los planos y fotos sobreviven a los
 * reinicios del servidor (el disco del plan gratuito de Render se borra). Se activa con {@code CIVIA_S3_BUCKET}.
 */
@Component
@ConditionalOnExpression("'${civia.storage.s3.bucket:}' != ''")
public class S3ObjectStore implements ObjectStore {

    private static final Logger LOG = LoggerFactory.getLogger(S3ObjectStore.class);

    private final S3Client s3;
    private final String bucket;

    public S3ObjectStore(@Value("${civia.storage.s3.bucket}") String bucket,
                         @Value("${civia.storage.s3.endpoint:}") String endpoint,
                         @Value("${civia.storage.s3.region:auto}") String region,
                         @Value("${civia.storage.s3.access-key:}") String accessKey,
                         @Value("${civia.storage.s3.secret-key:}") String secretKey) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("CIVIA_S3_BUCKET requiere CIVIA_S3_ACCESS_KEY y CIVIA_S3_SECRET_KEY.");
        }
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                // R2 y otros compatibles no aceptan las sumas de verificación nuevas que el SDK envía por defecto.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .forcePathStyle(true);
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        this.s3 = builder.build();
        this.bucket = bucket;
        LOG.info("Archivos en el almacenamiento externo (bucket {})", bucket);
    }

    @Override
    public void put(String key, Path file, long size) {
        s3.putObject(b -> b.bucket(bucket).key(key).contentLength(size).contentType("application/octet-stream"), RequestBody.fromFile(file));
    }

    @Override
    public InputStream get(String key) {
        try {
            return s3.getObject(b -> b.bucket(bucket).key(key));
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    @Override
    public long size(String key) {
        try {
            return s3.headObject(b -> b.bucket(bucket).key(key)).contentLength();
        } catch (NoSuchKeyException e) {
            return -1;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return -1;
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(bucket).key(key));
    }
}
