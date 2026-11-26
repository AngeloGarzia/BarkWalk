package com.barkwalk.storage.web;

import com.barkwalk.storage.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Lecture des fichiers média locaux (clés UUID non devinables).
@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final StorageService storageService;

    public MediaController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/{folder}/{filename:.+}")
    public ResponseEntity<Resource> oneLevel(@PathVariable String folder, @PathVariable String filename) {
        return serve(folder + "/" + filename);
    }

    @GetMapping("/{folder}/{sub}/{filename:.+}")
    public ResponseEntity<Resource> twoLevels(
            @PathVariable String folder, @PathVariable String sub, @PathVariable String filename) {
        return serve(folder + "/" + sub + "/" + filename);
    }

    @GetMapping("/{folder}/{sub}/{nested}/{filename:.+}")
    public ResponseEntity<Resource> threeLevels(
            @PathVariable String folder,
            @PathVariable String sub,
            @PathVariable String nested,
            @PathVariable String filename) {
        return serve(folder + "/" + sub + "/" + nested + "/" + filename);
    }

    private ResponseEntity<Resource> serve(String key) {
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
