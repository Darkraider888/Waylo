package com.mehedi.waylo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateProfileRequest {
    @NotBlank @Size(max = 100)
    private String fullName;
    @Size(max = 500)
    private String bio;
    @Size(max = 150)
    private String profileLocation;
}
