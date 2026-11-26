package com.barkwalk.booking.web;

import com.barkwalk.booking.dto.BookingDtos.BookingResponse;
import com.barkwalk.booking.dto.BookingDtos.WaitlistEntryResponse;
import com.barkwalk.booking.service.WaitlistService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/mine")
    public List<WaitlistEntryResponse> mine() {
        return waitlistService.mine();
    }

    @PostMapping("/{entryId}/accept")
    public BookingResponse accept(@PathVariable Long entryId) {
        return waitlistService.acceptOffer(entryId);
    }
}
