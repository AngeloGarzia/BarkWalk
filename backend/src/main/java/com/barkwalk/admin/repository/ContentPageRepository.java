package com.barkwalk.admin.repository;

import com.barkwalk.admin.domain.ContentPage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
// Pages de contenu.

public interface ContentPageRepository extends JpaRepository<ContentPage, Long> {
    Optional<ContentPage> findBySlug(String slug);
}
