package com.mehedi.waylo.service;

import com.mehedi.waylo.entity.Trip;
import com.mehedi.waylo.enums.TripStatus;
import com.mehedi.waylo.repository.TripRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TripService {

    private final TripRepository tripRepository;

    public TripService(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    @Transactional(readOnly = true)
    public List<Trip> getPublishedTrips() {
        return tripRepository.findByStatus(TripStatus.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public List<Trip> searchPublishedTrips(String term, Long destinationId) {
        return tripRepository.searchPublished(
                TripStatus.PUBLISHED, term, destinationId,
                java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Dhaka")));
    }

    @Transactional(readOnly = true)
    public Trip getPublishedTrip(Long id) {
        Trip trip = tripRepository.findByIdAndStatus(id, TripStatus.PUBLISHED)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));

        if (trip.getStatus() != TripStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }

        return trip;
    }
}