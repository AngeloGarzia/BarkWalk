package com.barkwalk.walk.web;

import com.barkwalk.walk.dto.WalkDtos.WalkCreateRequest;
import com.barkwalk.walk.dto.WalkDtos.WalkRecycleRequest;
import com.barkwalk.walk.dto.WalkDtos.WalkResponse;
import com.barkwalk.walk.dto.WalkDtos.WalkUpdateRequest;
import com.barkwalk.walk.dto.WalkParticipantDtos.ParticipantsResponse;
import com.barkwalk.walk.dto.WalkPhotoDtos.WalkHistoryResponse;
import com.barkwalk.walk.dto.WalkPhotoDtos.WalkPhotoResponse;
import com.barkwalk.walk.service.WalkPhotoService;
import com.barkwalk.walk.service.WalkService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// API publique de lecture des balades ; création / édition réservées aux rôles autorisés.
@RestController
@RequestMapping("/api/walks")
public class WalkController {
    private final WalkService walkService;
    private final WalkPhotoService walkPhotoService;

    public WalkController(WalkService walkService, WalkPhotoService walkPhotoService) {
        this.walkService = walkService;
        this.walkPhotoService = walkPhotoService;
    }

    @GetMapping
    public List<WalkResponse> catalogue(
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Instant from) {
        return walkService.catalogue(cityId, type, from);
    }

    @GetMapping("/mine")
    public List<WalkResponse> mine() {
        return walkService.mine();
    }

    /** Balades archivées — utilisateurs connectés (filtre ville optionnel). */
    @GetMapping("/archived")
    public List<WalkResponse> archivedCatalogue(@RequestParam(required = false) Long cityId) {
        return walkService.archivedCatalogue(cityId);
    }

    /** Vue admin : toutes les balades (région/département côté UI). Filtre ville / statut optionnels. */
    @GetMapping("/admin-overview")
    public List<WalkResponse> adminOverview(
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String status) {
        return walkService.adminOverview(cityId, status);
    }

    @GetMapping("/{id}")
    public WalkResponse get(@PathVariable Long id) {
        return walkService.get(id);
    }

    @GetMapping("/{id}/participants")
    public ParticipantsResponse participants(@PathVariable Long id) {
        return walkService.participants(id);
    }

    @GetMapping("/{id}/photos")
    public List<WalkPhotoResponse> photos(
            @PathVariable Long id, @RequestParam(required = false) String kind) {
        return walkPhotoService.list(id, kind);
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public WalkPhotoResponse uploadPhoto(
            @PathVariable Long id,
            @RequestParam(defaultValue = "PROMO") String kind,
            @RequestPart("file") MultipartFile file) {
        return walkPhotoService.upload(id, kind, file);
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@PathVariable Long id, @PathVariable Long photoId) {
        walkPhotoService.delete(id, photoId);
    }

    @GetMapping("/{id}/history")
    public WalkHistoryResponse history(@PathVariable Long id) {
        return walkService.history(id);
    }

    @PostMapping("/{id}/recycle")
    @ResponseStatus(HttpStatus.CREATED)
    public WalkResponse recycle(@PathVariable Long id, @Valid @RequestBody WalkRecycleRequest request) {
        return walkService.recycle(id, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalkResponse create(@Valid @RequestBody WalkCreateRequest request) {
        return walkService.create(request);
    }

    @PutMapping("/{id}")
    public WalkResponse update(@PathVariable Long id, @Valid @RequestBody WalkUpdateRequest request) {
        return walkService.update(id, request);
    }

    @PostMapping("/{id}/start")
    public WalkResponse start(@PathVariable Long id) {
        return walkService.start(id);
    }

    @PostMapping("/{id}/complete")
    public WalkResponse complete(@PathVariable Long id) {
        return walkService.complete(id);
    }

    /** Archive soft — créateur / encadrant / admin. Pas de suppression. */
    @PostMapping("/{id}/archive")
    public WalkResponse archive(@PathVariable Long id) {
        return walkService.archive(id);
    }
}
