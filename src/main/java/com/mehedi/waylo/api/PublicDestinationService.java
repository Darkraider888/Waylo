package com.mehedi.waylo.api;

import com.mehedi.waylo.repository.DestinationRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PublicDestinationService {
    public record DestinationSummary(Long id, String name, String district,
                                     String country, BigDecimal latitude, BigDecimal longitude) { }

    private final DestinationRepository repo;

    public PublicDestinationService(DestinationRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "publicDestinations", key = "'all'")
    public List<DestinationSummary> all() {
        return repo.findAll(Sort.by("name")).stream()
                .map(d -> new DestinationSummary(d.getId(), d.getName(), d.getDistrict(),
                        d.getCountry(), d.getLatitude(), d.getLongitude()))
                .toList();
    }
}
