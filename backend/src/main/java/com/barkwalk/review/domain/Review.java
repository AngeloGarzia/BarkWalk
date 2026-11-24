package com.barkwalk.review.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reviews")
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "walk_id", nullable = false) private Long walkId;
    @Column(name = "author_id", nullable = false) private Long authorId;
    @Column(name = "target_user_id", nullable = false) private Long targetUserId;
    @Column(nullable = false) private int rating;
    private String comment;
    private String reply;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    public Long getId() { return id; }
    public Long getWalkId() { return walkId; }
    public void setWalkId(Long walkId) { this.walkId = walkId; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public Instant getCreatedAt() { return createdAt; }
}
