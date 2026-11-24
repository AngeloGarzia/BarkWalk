package com.barkwalk.dog.web;

import com.barkwalk.dog.service.DogService;
import com.barkwalk.dog.web.DogDtos.DogResponse;
import com.barkwalk.dog.web.DogDtos.RefuseRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dogs")
public class AdminDogController {
    private final DogService dogService;

    public AdminDogController(DogService dogService) { this.dogService = dogService; }

    @GetMapping("/pending")
    public List<DogResponse> pending() { return dogService.pending(); }

    @PostMapping("/{id}/validate")
    public DogResponse validate(@PathVariable Long id) { return dogService.validate(id); }

    @PostMapping("/{id}/refuse")
    public DogResponse refuse(@PathVariable Long id, @Valid @RequestBody RefuseRequest request) {
        return dogService.refuse(id, request.reason());
    }
}
