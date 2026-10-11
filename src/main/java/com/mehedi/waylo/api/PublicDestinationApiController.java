package com.mehedi.waylo.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/destinations")
public class PublicDestinationApiController {
    private final PublicDestinationService destinations;

    public PublicDestinationApiController(PublicDestinationService destinations) {
        this.destinations = destinations;
    }

    @GetMapping
    public List<PublicDestinationService.DestinationSummary> list() {
        return destinations.all();
    }
}
