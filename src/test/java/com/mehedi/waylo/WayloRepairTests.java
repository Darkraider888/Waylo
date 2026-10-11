
package com.mehedi.waylo;

import com.mehedi.waylo.booking.BookingRepository;
import com.mehedi.waylo.booking.PaymentAttemptRepository;
import com.mehedi.waylo.controller.AdminController;
import com.mehedi.waylo.controller.AuthController;
import com.mehedi.waylo.dto.RegisterRequest;
import com.mehedi.waylo.entity.OrganizerApplication;
import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.enums.TripStatus;
import com.mehedi.waylo.repository.TripRepository;
import com.mehedi.waylo.service.AuthService;
import com.mehedi.waylo.service.OrganizerApplicationService;
import com.mehedi.waylo.service.TripService;
import com.mehedi.waylo.tripscope.TripProposalService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WayloRepairTests {

    @Test
    void searchResolvesExistingTemplate() {
        var controller = new com.mehedi.waylo.controller.SearchController(
                mock(TripService.class), mock(com.mehedi.waylo.repository.DestinationRepository.class),
                mock(com.mehedi.waylo.repository.UserRepository.class));
        String view = controller.search("", new ExtendedModelMap());
        assertNotNull(getClass().getResource("/templates/" + view + ".html"));
    }

    private AdminController adminController(
            OrganizerApplicationService service,
            TripProposalService proposals,
            TripRepository trips,
            BookingRepository bookings,
            PaymentAttemptRepository payments) {

        return new AdminController(
                service,
                proposals,
                trips,
                bookings,
                payments
        );
    }

    @Test
    void adminDashboardContainsPendingApplications() {

        var service = mock(OrganizerApplicationService.class);
        var proposals = mock(TripProposalService.class);
        var trips = mock(TripRepository.class);
        var bookings = mock(BookingRepository.class);
        var payments = mock(PaymentAttemptRepository.class);

        var applicant = new User();
        applicant.setFullName("Test Traveler");
        applicant.setEmail("test@example.com");

        var application = new OrganizerApplication();
        application.setId(42L);
        application.setUser(applicant);
        application.setOrganizationName("Travel Team");

        when(service.pendingApplications())
                .thenReturn(List.of(application));

        when(proposals.pending())
                .thenReturn(List.of());

        when(trips.findByStatus(TripStatus.PUBLISHED))
                .thenReturn(List.of());

        var controller = adminController(
                service,
                proposals,
                trips,
                bookings,
                payments
        );

        var model = new ExtendedModelMap();

        String view = controller.dashboard(model);

        assertEquals("admin/dashboard", view);

        assertTrue(model.containsAttribute("applications"));
        assertTrue(model.containsAttribute("tripProposals"));
        assertTrue(model.containsAttribute("publishedGroups"));

        assertEquals(
                List.of(application),
                model.get("applications")
        );

        verify(service).pendingApplications();
        verify(proposals).pending();
        verify(trips).findByStatus(TripStatus.PUBLISHED);
    }

    @Test
    void adminCanApproveOrganizerApplication() {

        var service = mock(OrganizerApplicationService.class);

        var controller = adminController(
                service,
                mock(TripProposalService.class),
                mock(TripRepository.class),
                mock(BookingRepository.class),
                mock(PaymentAttemptRepository.class)
        );

        var authentication =
                new UsernamePasswordAuthenticationToken(
                        "admin@example.com",
                        "unused"
                );

        String result = controller.approve(
                42L,
                authentication
        );

        assertEquals(
                "redirect:/admin/dashboard?approved",
                result
        );

        verify(service).review(
                42L,
                "admin@example.com",
                true,
                null
        );
    }

    @Test
    void adminReviewErrorReturnsToDashboard() {

        var controller = adminController(
                mock(OrganizerApplicationService.class),
                mock(TripProposalService.class),
                mock(TripRepository.class),
                mock(BookingRepository.class),
                mock(PaymentAttemptRepository.class)
        );

        var attributes = new RedirectAttributesModelMap();

        String view = controller.invalidReview(
                new IllegalArgumentException(
                        "Already reviewed"
                ),
                attributes
        );

        assertEquals(
                "redirect:/admin/dashboard",
                view
        );

        assertEquals(
                "Already reviewed",
                attributes.getFlashAttributes().get("reviewError")
        );
    }

    @Test
    void loginPageReturnsCorrectTemplate() {

        var authService = mock(AuthService.class);
        var controller = new AuthController(authService);

        assertEquals(
                "auth/login",
                controller.login()
        );
    }

    @Test
    void registrationPageProvidesRequestObject() {

        var controller = new AuthController(
                mock(AuthService.class)
        );

        var model = new ExtendedModelMap();

        String view = controller.registerPage(model);

        assertEquals("auth/register", view);

        assertInstanceOf(
                RegisterRequest.class,
                model.get("registerRequest")
        );
    }

    @Test
    void unpublishedOrMissingTripReturns404() {

        var repository = mock(TripRepository.class);

        when(repository.findByIdAndStatus(
                1L,
                TripStatus.PUBLISHED
        )).thenReturn(Optional.empty());

        var service = new TripService(repository);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getPublishedTrip(1L)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }
}
