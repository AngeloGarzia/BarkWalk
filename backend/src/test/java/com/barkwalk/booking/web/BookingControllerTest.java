package com.barkwalk.booking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.dto.BookingDtos.BookingRequest;
import com.barkwalk.booking.dto.BookingDtos.BookingResponse;
import com.barkwalk.booking.service.BookingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController bookingController;

    // Liste les réservations du compte connecté.
    @Test
    void mine_delegatesToService() {
        when(bookingService.mine()).thenReturn(List.of());
        assertThat(bookingController.mine()).isEmpty();
        verify(bookingService).mine();
    }

    // Crée une réservation à partir du corps HTTP.
    @Test
    void create_mapsBodyToBookingRequest() {
        BookingResponse response = sampleResponse();
        when(bookingService.create(any(BookingRequest.class))).thenReturn(response);

        BookingResponse result =
                bookingController.create(new BookingController.CreateBody(10L, 20L, true, false));

        assertThat(result).isSameAs(response);
        verify(bookingService).create(new BookingRequest(10L, 20L, true, false));
    }

    // Annule une réservation côté participant.
    @Test
    void cancel_delegatesToService() {
        BookingResponse response = sampleResponse();
        when(bookingService.cancel(5L)).thenReturn(response);
        assertThat(bookingController.cancel(5L)).isSameAs(response);
    }

    // Annule une réservation côté encadrant ou organisateur.
    @Test
    void cancelByStaff_forwardsOptionalReason() {
        BookingResponse response = sampleResponse();
        when(bookingService.cancelByStaff(eq(8L), eq("météo"))).thenReturn(response);
        assertThat(bookingController.cancelByStaff(8L, new BookingController.StaffCancelBody("météo")))
                .isSameAs(response);

        when(bookingService.cancelByStaff(eq(9L), isNull())).thenReturn(response);
        assertThat(bookingController.cancelByStaff(9L, null)).isSameAs(response);
    }

    private static BookingResponse sampleResponse() {
        return new BookingResponse(
                1L, 10L, 20L, "CONFIRMED", 0, false, null, null, false, false, null, null, null, null, null, null,
                null);
    }
}
