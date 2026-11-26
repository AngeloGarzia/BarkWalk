package com.barkwalk.booking.service;

import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.domain.WaitlistEntry;
import com.barkwalk.booking.dto.BookingDtos.BookingResponse;
import com.barkwalk.booking.dto.BookingDtos.WaitlistEntryResponse;
import com.barkwalk.city.domain.City;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.domain.WalkerProfile;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import org.springframework.stereotype.Component;

// Assemble les DTO réservation / waitlist avec libellés balade & chien pour l'UI.
@Component
public class BookingResponseFactory {
    private final WalkRepository walkRepository;
    private final DogRepository dogRepository;
    private final CityRepository cityRepository;
    private final PrivacyService privacyService;
    private final WalkerProfileRepository walkerProfileRepository;

    public BookingResponseFactory(
            WalkRepository walkRepository,
            DogRepository dogRepository,
            CityRepository cityRepository,
            PrivacyService privacyService,
            WalkerProfileRepository walkerProfileRepository) {
        this.walkRepository = walkRepository;
        this.dogRepository = dogRepository;
        this.cityRepository = cityRepository;
        this.privacyService = privacyService;
        this.walkerProfileRepository = walkerProfileRepository;
    }

    public BookingResponse from(Booking booking) {
        Walk walk = walkRepository.findById(booking.getWalkId()).orElse(null);
        String dogName = dogRepository.findById(booking.getDogId()).map(d -> d.getName()).orElse(null);
        return from(booking, walk, dogName);
    }

    public BookingResponse from(Booking booking, Walk walk, String dogName) {
        String cityName = null;
        if (walk != null) {
            cityName = cityRepository.findById(walk.getCityId()).map(City::getName).orElse(null);
        }
        return new BookingResponse(
                booking.getId(),
                booking.getWalkId(),
                booking.getDogId(),
                booking.getStatus(),
                booking.getPriceCents(),
                booking.isPaymentRequired(),
                booking.getConfirmedAt(),
                booking.getPaymentExpiresAt(),
                booking.isTrial(),
                booking.isCommunityAck(),
                walk != null ? walk.getTitle() : null,
                walk != null ? walk.getWalkType() : null,
                walk != null ? walk.getStartsAt() : null,
                cityName,
                dogName,
                organizerDisplayName(walk),
                privacyService.displayName(booking.getUserId()));
    }

    public WaitlistEntryResponse from(WaitlistEntry entry) {
        Walk walk = walkRepository.findById(entry.getWalkId()).orElse(null);
        String dogName = dogRepository.findById(entry.getDogId()).map(d -> d.getName()).orElse(null);
        return new WaitlistEntryResponse(
                entry.getId(),
                entry.getWalkId(),
                entry.getDogId(),
                entry.getPosition(),
                entry.getStatus(),
                entry.getOfferedAt(),
                entry.getOfferExpiresAt(),
                walk != null ? walk.getTitle() : null,
                walk != null ? walk.getWalkType() : null,
                walk != null ? walk.getStartsAt() : null,
                dogName,
                organizerDisplayName(walk));
    }

    private String organizerDisplayName(Walk walk) {
        if (walk == null) {
            return null;
        }
        if (walk.getOrganizerUserId() != null) {
            return privacyService.displayName(walk.getOrganizerUserId());
        }
        if (walk.getWalkerId() != null) {
            return walkerProfileRepository
                    .findById(walk.getWalkerId())
                    .map(WalkerProfile::getUserId)
                    .map(privacyService::displayName)
                    .orElse(null);
        }
        return null;
    }
}
