package com.barkwalk.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class StorageServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storageService;

    @BeforeEach
    void setUp() throws Exception {
        storageService = new StorageService(
                "barkwalk-docs", 15, tempDir.toString(), "test-signing-secret");
    }

    @Test
    void storeImage_detectsJpegDespiteWrongContentType() {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02, 0x03};
        MockMultipartFile file = new MockMultipartFile("file", "x.bin", "application/octet-stream", jpeg);

        StoredImage stored = storageService.storeImage("profiles/1", file);

        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(stored.storageKey()).endsWith(".jpg");
        assertThat(tempDir.resolve(stored.storageKey())).exists();
    }

    @Test
    void storeImage_detectsPngAndWebp() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        StoredImage pngStored = storageService.storeImage(
                "profiles/1", new MockMultipartFile("f", "a.txt", "text/plain", png));
        assertThat(pngStored.contentType()).isEqualTo("image/png");

        byte[] webp = new byte[] {
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50, 0x01
        };
        StoredImage webpStored = storageService.storeImage(
                "profiles/1", new MockMultipartFile("f", "a.txt", "image/jpeg", webp));
        assertThat(webpStored.contentType()).isEqualTo("image/webp");
    }

    @Test
    void storeImage_rejectsUnknownMagic() {
        byte[] junk = "not-an-image".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> storageService.storeImage(
                        "profiles/1", new MockMultipartFile("f", "a.jpg", "image/jpeg", junk)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void storeImage_rejectsOversize() {
        byte[] huge = new byte[5 * 1024 * 1024 + 1];
        huge[0] = (byte) 0xFF;
        huge[1] = (byte) 0xD8;
        huge[2] = (byte) 0xFF;
        assertThatThrownBy(() -> storageService.storeImage(
                        "profiles/1", new MockMultipartFile("f", "a.jpg", "image/jpeg", huge)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resolveSafe_rejectsPathTraversal() {
        assertThatThrownBy(() -> storageService.loadAsResource("../secret.txt"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void publicMediaUrl_includesExpAndSig() {
        String url = storageService.publicMediaUrl("profiles/1/photo.jpg");
        assertThat(url).startsWith("/api/media/profiles/1/photo.jpg?exp=");
        assertThat(url).contains("&sig=");
        storageService.assertValidMediaSignature(
                "profiles/1/photo.jpg",
                Long.parseLong(url.replaceAll(".*exp=(\\d+).*", "$1")),
                url.replaceAll(".*sig=([0-9a-f]+).*", "$1"));
    }
}
