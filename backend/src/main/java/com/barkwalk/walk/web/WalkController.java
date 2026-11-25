package com.barkwalk.walk.web;

import com.barkwalk.walk.service.WalkService;
import com.barkwalk.walk.dto.WalkDtos.WalkCreateRequest;
import com.barkwalk.walk.dto.WalkDtos.WalkResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// API publique de lecture des balades et création réservée aux rôles autorisés.
@RestController
@RequestMapping("/api/walks")
public class WalkController {
    private final WalkService walkService;

    public WalkController(WalkService walkService) { this.walkService = walkService; }

    @GetMapping
    public List<WalkResponse> catalogue(
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Instant from) {
        return walkService.catalogue(cityId, type, from);
    }

    @GetMapping("/{id}")
    public WalkResponse get(@PathVariable Long id) { return walkService.get(id); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public WalkResponse create(@Valid @RequestBody WalkCreateRequest request) {
        return walkService.create(request);
    }
}
