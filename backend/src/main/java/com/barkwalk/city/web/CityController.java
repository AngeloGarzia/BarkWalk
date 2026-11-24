package com.barkwalk.city.web;

import com.barkwalk.city.domain.City;
import com.barkwalk.city.domain.Zone;
import com.barkwalk.city.service.CityService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cities")
public class CityController {
    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @GetMapping
    public List<City> search(@RequestParam(required = false) String q) {
        return cityService.search(q);
    }

    @GetMapping("/{id}/zones")
    public List<Zone> zones(@PathVariable Long id) {
        return cityService.zones(id);
    }
}
