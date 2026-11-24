package com.barkwalk.admin.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "content_pages")
public class ContentPage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String slug;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private String body;
    @Column(nullable = false) private String version;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getVersion() { return version; }
}
