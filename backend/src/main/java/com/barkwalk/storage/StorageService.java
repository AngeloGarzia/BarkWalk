package com.barkwalk.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

// Stockage objet : disque local en dev + URLs /api/media/... signées HMAC ; stubs MinIO conservés.
@Service
public class StorageService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final byte[] JPEG_MAGIC = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] RIFF_MAGIC = new byte[] {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MAGIC = new byte[] {0x57, 0x45, 0x42, 0x50};

    private final String bucket;
    private final int signedUrlMinutes;
    private final Path localRoot;
    private final byte[] signingSecret;

    public StorageService(
            @Value("${barkwalk.s3.bucket}") String bucket,
            @Value("${barkwalk.s3.signed-url-minutes:15}") int signedUrlMinutes,
            @Value("${barkwalk.storage.local-path:./data/uploads}") String localPath,
            @Value("${barkwalk.storage.signing-secret}") String signingSecret)
            throws IOException {
        this.bucket = bucket;
        this.signedUrlMinutes = signedUrlMinutes;
        this.localRoot = Path.of(localPath).toAbsolutePath().normalize();
        this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(this.localRoot);
    }

    public String createObjectKey(String folder, String filename) {
        return folder + "/" + UUID.randomUUID() + "-" + sanitizeFilename(filename);
    }

    public StoredImage storeImage(String folder, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier image requis");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image trop lourde (max 5 Mo)");
        }
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(12);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier image illisible");
        }
        DetectedImage detected = detectImage(header);
        if (detected == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formats acceptés : JPEG, PNG, WebP");
        }
        String key = createObjectKey(folder, "photo." + detected.extension());
        Path target = resolveSafe(key);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Échec enregistrement image");
        }
        return new StoredImage(key, detected.contentType());
    }

    public void deleteObject(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolveSafe(storageKey));
        } catch (IOException ignored) {
            // best-effort
        }
    }

    public Resource loadAsResource(String storageKey) {
        try {
            Path path = resolveSafe(storageKey);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            return resource;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    public String publicMediaUrl(String storageKey) {
        long exp = Instant.now().plus(Duration.ofMinutes(signedUrlMinutes)).getEpochSecond();
        String sig = sign(storageKey, exp);
        return "/api/media/" + storageKey + "?exp=" + exp + "&sig=" + sig;
    }

    public void assertValidMediaSignature(String storageKey, Long exp, String sig) {
        if (storageKey == null || storageKey.isBlank() || exp == null || sig == null || sig.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Signature média manquante");
        }
        if (Instant.now().getEpochSecond() > exp) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "URL média expirée");
        }
        String expected = sign(storageKey, exp);
        if (!constantTimeEquals(expected, sig)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Signature média invalide");
        }
    }

    /** Dev-friendly signed URL stub (MinIO/S3 wiring can replace this). */
    public String signedGetUrl(String storageKey) {
        return "http://localhost:9000/" + bucket + "/" + storageKey + "?ttl="
                + Duration.ofMinutes(signedUrlMinutes).toMinutes();
    }

    public String signedPutUrl(String storageKey) {
        return signedGetUrl(storageKey) + "&method=PUT";
    }

    public String sign(String storageKey, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal((storageKey + exp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign media URL", e);
        }
    }

    static DetectedImage detectImage(byte[] header) {
        if (header == null || header.length < 3) {
            return null;
        }
        if (startsWith(header, JPEG_MAGIC)) {
            return new DetectedImage("image/jpeg", "jpg");
        }
        if (header.length >= 4 && startsWith(header, PNG_MAGIC)) {
            return new DetectedImage("image/png", "png");
        }
        if (header.length >= 12
                && startsWith(header, RIFF_MAGIC)
                && header[8] == WEBP_MAGIC[0]
                && header[9] == WEBP_MAGIC[1]
                && header[10] == WEBP_MAGIC[2]
                && header[11] == WEBP_MAGIC[3]) {
            return new DetectedImage("image/webp", "webp");
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }

    private Path resolveSafe(String storageKey) {
        Path resolved = localRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(localRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clé de stockage invalide");
        }
        return resolved;
    }

    private static String sanitizeFilename(String filename) {
        String name = filename == null || filename.isBlank() ? "file.bin" : filename;
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    record DetectedImage(String contentType, String extension) {}
}
