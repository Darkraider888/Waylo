package com.mehedi.waylo.service;

import com.mehedi.waylo.dto.OrganizerApplicationRequest;
import com.mehedi.waylo.entity.OrganizerApplication;
import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.enums.ApplicationStatus;
import com.mehedi.waylo.enums.Role;
import com.mehedi.waylo.repository.OrganizerApplicationRepository;
import com.mehedi.waylo.repository.UserRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class OrganizerApplicationService {

    private final OrganizerApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final AuditService audit;
    private final NotificationService notifications;

    public OrganizerApplicationService(
            OrganizerApplicationRepository applicationRepository,
            UserRepository userRepository, AuditService audit, NotificationService notifications) {

        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.audit=audit; this.notifications=notifications;
    }

    @Transactional
    public void apply(
            String email,
            OrganizerApplicationRequest request) {

        User user = findUser(email);
        userRepository.lockById(user.getId()).orElseThrow();

        if (user.getRole() != Role.MEMBER) {
            throw new IllegalArgumentException(
                    "Only members can apply to become Trip Managers."
            );
        }

        if (!user.isEnabled()) {
            throw new AccessDeniedException("Account is disabled.");
        }

        if (applicationRepository.existsByUserIdAndStatus(
                user.getId(),
                ApplicationStatus.PENDING_VERIFICATION)) {

            throw new IllegalArgumentException(
                    "You already have a pending application."
            );
        }

        OrganizerApplication application =
                new OrganizerApplication();

        application.setUser(user);

        application.setOrganizationName(
                request.getOrganizationName().trim()
        );

        application.setExperience(
                request.getExperience().trim()
        );

        application.setDocumentType(request.getDocumentType());

        // Real identity-document upload is not enabled.
        application.setDocumentReference(null);

        application.setStatus(
                ApplicationStatus.PENDING_VERIFICATION
        );

        applicationRepository.save(application);
    }

    @Transactional(readOnly = true)
    public OrganizerApplication latestApplication(String email) {

        User user = findUser(email);

        return applicationRepository
                .findFirstByUserIdOrderByAppliedAtDesc(user.getId())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<OrganizerApplication> pendingApplications() {

        return applicationRepository
                .findByStatusOrderByAppliedAtAsc(
                        ApplicationStatus.PENDING_VERIFICATION
                );
    }

    @Transactional
    public void review(
            Long applicationId,
            String adminEmail,
            boolean approve,
            String rejectionReason) {

        User admin = findUser(adminEmail);

        if (admin.getRole() != Role.ADMIN || !admin.isEnabled()) {
            throw new AccessDeniedException(
                    "Only Platform Admins can review applications."
            );
        }

        OrganizerApplication application =
                applicationRepository.findForReview(applicationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Application not found."
                                )
                        );

        if (application.getStatus()
                != ApplicationStatus.PENDING_VERIFICATION) {

            throw new IllegalArgumentException(
                    "This application has already been reviewed."
            );
        }

        User applicant = application.getUser();

        if (applicant.getRole() != Role.MEMBER
                || !applicant.isEnabled()) {

            throw new IllegalArgumentException(
                    "Applicant is not eligible for approval."
            );
        }

        if (!approve && (rejectionReason == null
                || rejectionReason.isBlank())) {

            throw new IllegalArgumentException(
                    "A rejection reason is required."
            );
        }

        if (!approve && rejectionReason.length() > 500) {
            throw new IllegalArgumentException(
                    "Rejection reason must be 500 characters or fewer."
            );
        }

        application.setReviewedBy(admin);
        application.setReviewedAt(LocalDateTime.now());

        if (approve) {

            // University demonstration approval only:
            // real identity verification is not yet implemented.
            application.setStatus(ApplicationStatus.VERIFIED);
            application.setRejectionReason(null);

            applicant.setRole(Role.ORGANIZER);
            userRepository.save(applicant);

        } else {

            application.setStatus(ApplicationStatus.REJECTED);
            application.setRejectionReason(rejectionReason.trim());
        }

        applicationRepository.save(application);
        audit.record(admin.getId(),approve?"ORGANIZER_APPROVED":"ORGANIZER_REJECTED","APPLICATION",applicationId,
                approve?"University demonstration approval; no identity verification":rejectionReason.trim());
        notifications.send(applicant.getId(),approve?"Your Trip Manager application was approved for the university demo. Sign in again to refresh your permissions.":"Your Trip Manager application was rejected. Open the application page for the reason.","/manager/apply");
    }

    private User findUser(String email) {

        if (email == null || email.isBlank()) {
            throw new AccessDeniedException(
                    "Authentication is required."
            );
        }

        return userRepository.findByEmail(
                email.trim().toLowerCase(Locale.ROOT)
        ).orElseThrow(() ->
                new IllegalArgumentException("Account not found.")
        );
    }
}