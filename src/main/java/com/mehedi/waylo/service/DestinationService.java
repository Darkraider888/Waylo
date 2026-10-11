package com.mehedi.waylo.service;

import com.mehedi.waylo.dto.DestinationRequest;
import com.mehedi.waylo.entity.Destination;
import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.enums.Role;
import com.mehedi.waylo.repository.DestinationRepository;
import com.mehedi.waylo.repository.TripRepository;
import com.mehedi.waylo.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class DestinationService {

    private final DestinationRepository destinations;
    private final TripRepository trips;
    private final UserRepository users;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final MediaStorageService media;
    private final AuditService audit;

    public DestinationService(
            DestinationRepository destinations,
            TripRepository trips,
            UserRepository users, org.springframework.jdbc.core.JdbcTemplate jdbc, MediaStorageService media, AuditService audit) {

        this.destinations = destinations;
        this.trips = trips;
        this.users = users;
        this.jdbc = jdbc; this.media = media; this.audit = audit;
    }

    private User authorized(String email, boolean adminOnly) {

        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("Sign in required.");
        }

        User user = users.findByEmail(
                email.trim().toLowerCase(Locale.ROOT)
        ).orElseThrow(() ->
                new AccessDeniedException("Account unavailable.")
        );

        if (!user.isEnabled()) {
            throw new AccessDeniedException("Account disabled.");
        }

        if (adminOnly) {

            if (user.getRole() != Role.ADMIN) {
                throw new AccessDeniedException(
                        "Platform Admin access required."
                );
            }

        } else if (user.getRole() != Role.ORGANIZER
                && user.getRole() != Role.ADMIN) {

            throw new AccessDeniedException(
                    "Approved Trip Manager access required."
            );
        }

        return user;
    }

    @Transactional(readOnly = true)
    public List<Destination> all(String email) {

        authorized(email, false);

        return destinations.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public List<Destination> search(String email, String term) {

        authorized(email, false);

        String query = term == null ? "" : term.trim();

        if (query.length() > 100) {
            query = query.substring(0, 100);
        }

        return destinations.search(query)
                .stream()
                .limit(60)
                .toList();
    }

    private String clean(String value) {

        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ");
    }

    private void validateCoordinates(DestinationRequest request) {

        boolean hasLatitude = request.getLatitude() != null;
        boolean hasLongitude = request.getLongitude() != null;

        if (hasLatitude && hasLongitude && (request.getLatitude().abs().compareTo(new java.math.BigDecimal("90"))>0
                || request.getLongitude().abs().compareTo(new java.math.BigDecimal("180"))>0))
            throw new IllegalArgumentException("Coordinates are outside the valid range.");
        if (hasLatitude != hasLongitude) {
            throw new IllegalArgumentException(
                    "Enter both coordinates or leave both empty."
            );
        }
    }

    @Transactional
    @CacheEvict(cacheNames = "publicDestinations", allEntries = true)
    public Destination add(
            String email,
            DestinationRequest request) {

        authorized(email, false);

        String name = clean(request.getName());
        String district = clean(request.getDistrict());
        String country = clean(request.getCountry());

        if (name.isBlank()
                || district.isBlank()
                || country.isBlank()) {

            throw new IllegalArgumentException(
                    "Name, district and country are required."
            );
        }

        if (name.length() > 150
                || district.length() > 120
                || country.length() > 120) {

            throw new IllegalArgumentException(
                    "Destination information is too long."
            );
        }

        if (request.getDescription() != null
                && request.getDescription().length() > 3000) {

            throw new IllegalArgumentException(
                    "Description is too long."
            );
        }

        validateCoordinates(request);

        boolean duplicate = destinations.findAll()
                .stream()
                .anyMatch(existing -> {

                    String existingDistrict = clean(
                            existing.getDistrict() != null
                                    ? existing.getDistrict()
                                    : existing.getLocation()
                    );

                    String existingCountry = clean(
                            existing.getCountry() != null
                                    ? existing.getCountry()
                                    : "Bangladesh"
                    );

                    return existing.getName().equalsIgnoreCase(name)
                            && existingDistrict.equalsIgnoreCase(district)
                            && existingCountry.equalsIgnoreCase(country);
                });

        if (duplicate) {
            throw new IllegalArgumentException(
                    "This destination already exists."
            );
        }

        Destination destination = new Destination();

        destination.setName(name);
        destination.setDistrict(district);
        destination.setCountry(country);
        destination.setLocation(district + ", " + country);

        destination.setDescription(
                request.getDescription() == null
                        ? null
                        : request.getDescription().trim()
        );

        destination.setLatitude(request.getLatitude());
        destination.setLongitude(request.getLongitude());

        destination = destinations.saveAndFlush(destination);
        reserveIdentity(destination);
        audit.record(authorized(email,false).getId(),"DESTINATION_CREATED","DESTINATION",destination.getId(),"Created public destination");
        return destination;
    }

    private void reserveIdentity(Destination destination) {
        try {
            jdbc.update("INSERT INTO destination_keys(name_key,district_key,country_key,destination_id) VALUES(?,?,?,?)",
                    clean(destination.getName()).toLowerCase(Locale.ROOT), clean(destination.getDistrict()).toLowerCase(Locale.ROOT),
                    clean(destination.getCountry()).toLowerCase(Locale.ROOT), destination.getId());
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new IllegalArgumentException("This destination already exists.");
        }
    }

    @Transactional
    @CacheEvict(cacheNames="publicDestinations",allEntries=true)
    public void update(String email, Long id, DestinationRequest request, org.springframework.web.multipart.MultipartFile photo) {
        User admin=authorized(email,true);
        Destination d=destinations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String name=clean(request.getName()), district=clean(request.getDistrict()), country=clean(request.getCountry());
        if(name.isEmpty()||name.length()>150||district.isEmpty()||district.length()>120||country.isEmpty()||country.length()>120
                || (request.getDescription()!=null && request.getDescription().length()>3000))
            throw new IllegalArgumentException("Check destination fields and lengths.");
        validateCoordinates(request);
        boolean duplicate=destinations.findAll().stream().anyMatch(other -> !other.getId().equals(id)
                && clean(other.getName()).equalsIgnoreCase(name)
                && clean(other.getDistrict()==null?other.getLocation():other.getDistrict()).equalsIgnoreCase(district)
                && clean(other.getCountry()==null?"Bangladesh":other.getCountry()).equalsIgnoreCase(country));
        if(duplicate)throw new IllegalArgumentException("This destination already exists.");
        jdbc.update("DELETE FROM destination_keys WHERE destination_id=?",id);
        d.setName(name);d.setDistrict(district);d.setCountry(country);d.setLocation(district+", "+country);
        d.setDescription(request.getDescription());d.setLatitude(request.getLatitude());d.setLongitude(request.getLongitude());
        reserveIdentity(d);
        var upload=media.store(photo,MediaStorageService.PhotoKind.DESTINATION);
        String oldId=d.getImagePublicId(),oldUrl=d.getImageUrl();
        if(upload!=null){d.setImageUrl(upload.url());d.setImagePublicId(upload.publicId());}
        destinations.saveAndFlush(d);
        if(upload!=null)media.deleteAfterCommit(oldId,oldUrl);
        audit.record(admin.getId(),"DESTINATION_UPDATED","DESTINATION",id,"Updated destination details or public photo");
    }

    @Transactional
    @CacheEvict(cacheNames = "publicDestinations", allEntries = true)
    public void delete(String email, Long id) {

        authorized(email, true);

        Destination destination = destinations.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Destination not found."
                        )
                );

        if (trips.existsByDestinationId(id)) {
            throw new IllegalArgumentException(
                    "This destination is used by trips and cannot be deleted."
            );
        }

        try {
            destinations.delete(destination);
            destinations.flush();
            audit.record(authorized(email,true).getId(),"DESTINATION_DELETED","DESTINATION",id,"Removed unused destination");
            media.deleteAfterCommit(destination.getImagePublicId(), destination.getImageUrl());
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException(
                    "This destination is currently in use."
            );
        }
    }
}