package com.barkwalk.booking.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// Balade complète : le chien a été placé en liste d'attente. La transaction doit être validée
// malgré l'exception (voir noRollbackFor sur BookingService.create).
public class WalkFullException extends ResponseStatusException {
    public WalkFullException() {
        super(HttpStatus.CONFLICT, "Walk full — added to waitlist");
    }
}
