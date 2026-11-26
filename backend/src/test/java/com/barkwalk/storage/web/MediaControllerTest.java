package com.barkwalk.storage.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barkwalk.storage.StorageService;
import com.barkwalk.storage.StoredImage;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MediaControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;
    private StorageService storageService;

    @BeforeEach
    void setUp() throws Exception {
        storageService = new StorageService("bucket", 15, tempDir.toString(), "media-test-secret");
        mockMvc = MockMvcBuilders.standaloneSetup(new MediaController(storageService)).build();
    }

    @Test
    void validSignature_returnsImage() throws Exception {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x11, 0x22};
        StoredImage stored = storageService.storeImage(
                "profiles/1", new MockMultipartFile("f", "a.bin", "application/octet-stream", jpeg));
        String url = storageService.publicMediaUrl(stored.storageKey());
        String path = url.substring(0, url.indexOf('?'));
        String query = url.substring(url.indexOf('?') + 1);
        String exp = query.split("&")[0].substring("exp=".length());
        String sig = query.split("&")[1].substring("sig=".length());

        mockMvc.perform(get(path).param("exp", exp).param("sig", sig))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "private, max-age=86400"));
    }

    @Test
    void invalidSignature_returns403() throws Exception {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x11};
        StoredImage stored = storageService.storeImage(
                "profiles/1", new MockMultipartFile("f", "a.jpg", "image/jpeg", jpeg));
        long exp = Instant.now().plusSeconds(60).getEpochSecond();

        mockMvc.perform(get("/api/media/" + stored.storageKey())
                        .param("exp", String.valueOf(exp))
                        .param("sig", "deadbeef"))
                .andExpect(status().isForbidden());
    }

    @Test
    void expiredSignature_returns403() throws Exception {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x33};
        StoredImage stored = storageService.storeImage(
                "profiles/1", new MockMultipartFile("f", "a.jpg", "image/jpeg", jpeg));
        long exp = Instant.now().minusSeconds(30).getEpochSecond();
        String sig = storageService.sign(stored.storageKey(), exp);

        mockMvc.perform(get("/api/media/" + stored.storageKey())
                        .param("exp", String.valueOf(exp))
                        .param("sig", sig))
                .andExpect(status().isForbidden());
    }
}
