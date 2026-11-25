package com.barkwalk.attendance.domain;

import jakarta.persistence.*;
import java.time.Instant;

// Présence liée à une réservation.
@Entity
@Table(name = "attendance")
public class Attendance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "booking_id", nullable = false, unique = true) private Long bookingId;
    @Column(name = "departure_status") private String departureStatus;
    @Column(name = "departure_at") private Instant departureAt;
    @Column(name = "return_status") private String returnStatus;
    @Column(name = "return_at") private Instant returnAt;
    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public String getDepartureStatus() { return departureStatus; }
    public void setDepartureStatus(String departureStatus) { this.departureStatus = departureStatus; }
    public Instant getDepartureAt() { return departureAt; }
    public void setDepartureAt(Instant departureAt) { this.departureAt = departureAt; }
    public String getReturnStatus() { return returnStatus; }
    public void setReturnStatus(String returnStatus) { this.returnStatus = returnStatus; }
    public Instant getReturnAt() { return returnAt; }
    public void setReturnAt(Instant returnAt) { this.returnAt = returnAt; }
}
