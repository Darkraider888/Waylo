package com.mehedi.waylo.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DestinationRequest {

    @NotBlank(message = "Destination name is required")
    @Size(max = 150)
    private String name;

    @NotBlank(message = "District or region is required")
    @Size(max = 120)
    private String district;

    @NotBlank(message = "Country is required")
    @Size(max = 120)
    private String country = "Bangladesh";

    @Size(max = 3000)
    private String description;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private BigDecimal latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private BigDecimal longitude;
}