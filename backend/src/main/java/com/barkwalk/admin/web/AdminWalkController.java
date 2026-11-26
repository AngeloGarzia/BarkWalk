package com.barkwalk.admin.web;

import com.barkwalk.walk.dto.WalkDtos.WalkResponse;
import com.barkwalk.walk.service.WalkService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestion admin des balades archivées / suppression définitive. */
@RestController
@RequestMapping("/api/admin/walks")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWalkController {
    private final WalkService walkService;

    public AdminWalkController(WalkService walkService) {
        this.walkService = walkService;
    }

    @GetMapping("/archived")
    public List<WalkResponse> archived() {
        return walkService.archived();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        walkService.deleteByAdmin(id);
    }
}
