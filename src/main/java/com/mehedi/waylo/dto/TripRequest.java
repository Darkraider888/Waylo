package com.mehedi.waylo.dto;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
public class TripRequest {
    @NotBlank @Size(max=200) private String title;
    @NotBlank @Size(min=20,max=5000) private String description;
    @NotNull private Long destinationId;
    @NotNull @DateTimeFormat(pattern="yyyy-MM-dd'T'HH:mm")
    private LocalDateTime departureDate;
    @NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2)
    private BigDecimal price;
    @NotNull @Min(1) @Max(1000) private Integer capacity;
}
