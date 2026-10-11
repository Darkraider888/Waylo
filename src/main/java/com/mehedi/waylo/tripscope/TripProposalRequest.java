package com.mehedi.waylo.tripscope;

import com.mehedi.waylo.enums.DocumentType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
public class TripProposalRequest {
    @NotBlank @Size(min = 8, max = 200)
    private String title;

    @NotBlank @Size(min = 30, max = 5000)
    private String description;

    // Either an existing destination OR a suggested new one is required.
    // This conditional rule is enforced in TripProposalService.
    private Long destinationId;

    @Size(max = 150)
    private String suggestedDestinationName;

    @Size(max = 120)
    private String suggestedDistrict;

    @Size(max = 120)
    private String suggestedCountry = "Bangladesh";

    @Size(max = 1000)
    private String suggestedDescription;

    @DecimalMin("-90.0") @DecimalMax("90.0")
    private BigDecimal suggestedLatitude;

    @DecimalMin("-180.0") @DecimalMax("180.0")
    private BigDecimal suggestedLongitude;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime departureDate;

    @NotNull @DecimalMin("10.00") @DecimalMax("99999999.99")
    private BigDecimal price;

    @Min(1) @Max(1000)
    private int capacity = 10;

    @NotBlank @Size(min = 30, max = 1500)
    private String motivation;

    @NotNull
    private DocumentType documentType;
}
