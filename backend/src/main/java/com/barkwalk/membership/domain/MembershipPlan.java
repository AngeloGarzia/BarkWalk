package com.barkwalk.membership.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "membership_plans")
public class MembershipPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false) private String name;
    private String description;
    @Column(name = "price_cents", nullable = false) private int priceCents;
    @Column(nullable = false) private String currency = "EUR";
    @Column(name = "interval_unit", nullable = false) private String intervalUnit;
    @Column(name = "stripe_price_id") private String stripePriceId;
    @Column(nullable = false) private boolean active = true;
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getPriceCents() { return priceCents; }
    public String getCurrency() { return currency; }
    public String getIntervalUnit() { return intervalUnit; }
    public String getStripePriceId() { return stripePriceId; }
    public boolean isActive() { return active; }
}
