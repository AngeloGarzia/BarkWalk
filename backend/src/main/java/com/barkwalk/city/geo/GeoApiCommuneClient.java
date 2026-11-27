package com.barkwalk.city.geo;

import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Client geo.api.gouv.fr — référentiel officiel des communes françaises.
@Component
public class GeoApiCommuneClient {
    private static final Logger log = LoggerFactory.getLogger(GeoApiCommuneClient.class);
    private static final String BASE = "https://geo.api.gouv.fr";
    private static final String FIELDS = "nom,code,codesPostaux,centre,departement,region";

    private final RestClient restClient;

    public GeoApiCommuneClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(BASE).build();
    }

    public List<GeoCommune> searchByName(String name, int limit) {
        return get("/communes", "nom", name, limit, true);
    }

    public List<GeoCommune> searchByPostalCode(String postalCode, int limit) {
        return get("/communes", "codePostal", postalCode, limit, false);
    }

    private List<GeoCommune> get(String path, String param, String value, int limit, boolean boost) {
        try {
            var spec = restClient
                    .get()
                    .uri(uriBuilder -> {
                        uriBuilder
                                .path(path)
                                .queryParam(param, value)
                                .queryParam("fields", FIELDS)
                                .queryParam("limit", limit);
                        if (boost) {
                            uriBuilder.queryParam("boost", "population");
                        }
                        return uriBuilder.build();
                    });
            GeoCommune[] body = spec.retrieve().body(GeoCommune[].class);
            if (body == null || body.length == 0) {
                return List.of();
            }
            return Arrays.asList(body);
        } catch (RestClientException ex) {
            log.warn(
                    "geo.api.gouv.fr unavailable for {}={}: {}",
                    sanitizeLog(param),
                    sanitizeLog(value),
                    sanitizeLog(ex.getMessage()));
            return List.of();
        }
    }

    private static String sanitizeLog(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', '_').replace('\n', '_');
    }

    public record GeoCommune(
            String nom,
            String code,
            List<String> codesPostaux,
            GeoPoint centre,
            GeoNamed departement,
            GeoNamed region) {}

    public record GeoPoint(String type, List<Double> coordinates) {}

    public record GeoNamed(String code, String nom) {}
}
