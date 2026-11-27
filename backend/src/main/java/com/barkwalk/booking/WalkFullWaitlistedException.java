package com.barkwalk.booking;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * R7 : balade pleine — l'inscription waitlist a été enregistrée. Exclue du rollback
 * transactionnel pour que l'entrée WAITING soit bien persistée.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Walk full — added to waitlist")
public class WalkFullWaitlistedException extends RuntimeException {
    public WalkFullWaitlistedException() {
        super("Walk full — added to waitlist");
    }
}
