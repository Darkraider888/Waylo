package com.mehedi.waylo.dto;

import com.mehedi.waylo.enums.DocumentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class OrganizerApplicationRequest {

    @NotBlank(
            message = "Organization name is required"
    )
    @Size(max = 150)
    private String organizationName;

    @NotBlank(
            message = "Describe your travel organizing experience"
    )
    @Size(
            min = 20,
            max = 2000,
            message = "Experience must contain 20 to 2000 characters"
    )
    private String experience;

    @NotNull(
            message = "Select NID or Passport"
    )
    private DocumentType documentType;

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }
}