package com.barkwalk.dog.web;

import com.barkwalk.dog.service.DogService;
import com.barkwalk.dog.dto.DogDtos.DogRequest;
import com.barkwalk.dog.dto.DogDtos.DogResponse;
import com.barkwalk.dog.dto.DogDtos.DocumentRequest;
import com.barkwalk.dog.dto.DogDtos.DocumentResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// API propriétaire pour les chiens.
@RestController
@RequestMapping("/api/dogs")
public class DogController {
    private final DogService dogService;

    public DogController(DogService dogService) { this.dogService = dogService; }

    @GetMapping public List<DogResponse> list() { return dogService.myDogs(); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public DogResponse create(@Valid @RequestBody DogRequest request) { return dogService.create(request); }

    @PutMapping("/{id}")
    public DogResponse update(@PathVariable Long id, @Valid @RequestBody DogRequest request) {
        return dogService.update(id, request);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { dogService.delete(id); }

    @PostMapping("/{id}/submit")
    public DogResponse submit(@PathVariable Long id) { return dogService.submit(id); }

    @GetMapping("/{id}/documents")
    public List<DocumentResponse> documents(@PathVariable Long id) { return dogService.documents(id); }

    @PostMapping("/{id}/documents") @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse addDocument(@PathVariable Long id, @Valid @RequestBody DocumentRequest request) {
        return dogService.addDocument(id, request);
    }
}
