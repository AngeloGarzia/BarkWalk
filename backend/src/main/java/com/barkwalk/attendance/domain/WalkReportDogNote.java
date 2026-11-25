package com.barkwalk.attendance.domain;

import jakarta.persistence.*;

// Note comportementale par chien dans un compte-rendu.
@Entity
@Table(name = "walk_report_dog_notes")
public class WalkReportDogNote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "report_id", nullable = false) private Long reportId;
    @Column(name = "dog_id", nullable = false) private Long dogId;
    @Column(name = "behavior_score") private Integer behaviorScore;
    private String comment;
    public Long getId() { return id; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getDogId() { return dogId; }
    public void setDogId(Long dogId) { this.dogId = dogId; }
    public Integer getBehaviorScore() { return behaviorScore; }
    public void setBehaviorScore(Integer behaviorScore) { this.behaviorScore = behaviorScore; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
