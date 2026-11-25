package com.barkwalk.city.web;

import com.barkwalk.city.dto.CityDtos.CityResponse;
import com.barkwalk.city.dto.CityDtos.ZoneResponse;
import com.barkwalk.city.service.CityService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// API référentiel villes (publique).
@RestController
@RequestMapping("/api/cities")
public class CityController {
    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @GetMapping
    public List<CityResponse> search(@RequestParam(required = false) String q) {
        return cityService.search(q);
    }

    @GetMapping("/{id}/zones")
    public List<ZoneResponse> zones(@PathVariable Long id) {
        return cityService.zones(id);
    }
}
