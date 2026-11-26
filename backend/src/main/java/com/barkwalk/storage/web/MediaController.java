package com.barkwalk.storage.web;

import com.barkwalk.storage.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Lecture des fichiers média locaux via URL signée (HMAC + expiration).
@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final StorageService storageService;

    public MediaController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/{folder}/{filename:.+}")
    public ResponseEntity<Resource> oneLevel(
            @PathVariable String folder,
            @PathVariable String filename,
            @RequestParam(required = false) Long exp,
            @RequestParam(required = false) String sig) {
        return serve(folder + "/" + filename, exp, sig);
    }

    @GetMapping("/{folder}/{sub}/{filename:.+}")
    public ResponseEntity<Resource> twoLevels(
            @PathVariable String folder,
            @PathVariable String sub,
            @PathVariable String filename,
            @RequestParam(required = false) Long exp,
            @RequestParam(required = false) String sig) {
        return serve(folder + "/" + sub + "/" + filename, exp, sig);
    }

    @GetMapping("/{folder}/{sub}/{nested}/{filename:.+}")
    public ResponseEntity<Resource> threeLevels(
            @PathVariable String folder,
            @PathVariable String sub,
            @PathVariable String nested,
            @PathVariable String filename,
            @RequestParam(required = false) Long exp,
            @RequestParam(required = false) String sig) {
        return serve(folder + "/" + sub + "/" + nested + "/" + filename, exp, sig);
    }

    private ResponseEntity<Resource> serve(String key, Long exp, String sig) {
        storageService.assertValidMediaSignature(key, exp, sig);
        Resource resource = storageService.loadAsResource(key);
        String filename = key.substring(key.lastIndexOf('/') + 1).toLowerCase();
        MediaType type = MediaType.IMAGE_JPEG;
        if (filename.endsWith(".png")) {
            type = MediaType.IMAGE_PNG;
        } else if (filename.endsWith(".webp")) {
            type = MediaType.parseMediaType("image/webp");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .contentType(type)
                .body(resource);
    }
}
