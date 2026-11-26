package com.barkwalk.booking.web;

import com.barkwalk.booking.dto.BookingDtos.BookingRequest;
import com.barkwalk.booking.dto.BookingDtos.BookingResponse;
import com.barkwalk.booking.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// Endpoints de réservation pour l'adhérent connecté.
@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    public record CreateBody(@NotNull Long walkId, @NotNull Long dogId, boolean communityAck, boolean trial) {}

    @GetMapping
    public List<BookingResponse> mine() {
        return bookingService.mine();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody CreateBody body) {
        return bookingService.create(new BookingRequest(body.walkId(), body.dogId(), body.communityAck(), body.trial()));
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id) {
        return bookingService.cancel(id);
    }

    public record StaffCancelBody(String reason) {}

    @PostMapping("/{id}/cancel-by-staff")
    public BookingResponse cancelByStaff(@PathVariable Long id, @RequestBody(required = false) StaffCancelBody body) {
        return bookingService.cancelByStaff(id, body != null ? body.reason() : null);
    }
}
