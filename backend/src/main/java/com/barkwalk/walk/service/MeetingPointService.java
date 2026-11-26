package com.barkwalk.walk.service;

import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.walk.domain.MeetingPoint;
import com.barkwalk.walk.dto.MeetingPointDtos.MeetingPointCreateRequest;
import com.barkwalk.walk.dto.MeetingPointDtos.MeetingPointResponse;
import com.barkwalk.walk.repository.MeetingPointRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Points de rendez-vous actifs par commune (lecture + création carte).
@Service
public class MeetingPointService {
    private final MeetingPointRepository meetingPointRepository;
    private final CityRepository cityRepository;

    public MeetingPointService(MeetingPointRepository meetingPointRepository, CityRepository cityRepository) {
        this.meetingPointRepository = meetingPointRepository;
        this.cityRepository = cityRepository;
    }

    @Transactional(readOnly = true)
    public List<MeetingPointResponse> listByCity(Long cityId) {
        return meetingPointRepository.findByCityIdAndActiveTrue(cityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MeetingPointResponse create(MeetingPointCreateRequest request) {
        if (!cityRepository.existsById(request.cityId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "City not found");
        }
        MeetingPoint mp = new MeetingPoint();
        mp.setCityId(request.cityId());
        mp.setName(request.name().trim());
        mp.setAddress(request.address().trim());
        mp.setLat(request.lat());
        mp.setLng(request.lng());
        mp.setActive(true);
        return toResponse(meetingPointRepository.save(mp));
    }

    private MeetingPointResponse toResponse(MeetingPoint mp) {
        return new MeetingPointResponse(
                mp.getId(), mp.getCityId(), mp.getName(), mp.getAddress(), mp.getLat(), mp.getLng());
    }
}
