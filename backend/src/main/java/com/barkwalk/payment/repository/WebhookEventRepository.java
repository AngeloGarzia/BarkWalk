package com.barkwalk.payment.repository;

import com.barkwalk.payment.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
// Idempotence des webhooks.

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    boolean existsByProviderAndEventId(String provider, String eventId);
}
