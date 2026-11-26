package com.barkwalk.booking.web;

import com.barkwalk.booking.dto.BookingDtos.BookingResponse;
import com.barkwalk.booking.service.WaitlistService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/waitlist")
public class WaitlistController {
    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping("/{entryId}/accept")
    public BookingResponse accept(@PathVariable Long entryId) {
        return waitlistService.acceptOffer(entryId);
    }
}
