package com.mehedi.waylo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreatePostRequest {
    @NotBlank(message = "Write a caption for your journey.")
    @Size(max = 2000)
    private String caption;
    private Long destinationId;
    private Long tripId;
}
