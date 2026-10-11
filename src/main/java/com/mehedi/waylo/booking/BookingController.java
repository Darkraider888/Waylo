package com.mehedi.waylo.booking;

import com.mehedi.waylo.groups.TripGroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BookingController {
    private static final Logger log = LoggerFactory.getLogger(BookingController.class);
    private final BookingService bookings;
    private final PaymentAttemptRepository attempts;
    private final SandboxPaymentGateway gateway;
    private final TripGroupService groups;

    public BookingController(BookingService bookings, PaymentAttemptRepository attempts,
                             SandboxPaymentGateway gateway, TripGroupService groups) {
        this.bookings = bookings;
        this.attempts = attempts;
        this.gateway = gateway;
        this.groups = groups;
    }

    @PostMapping("/trips/{tripId}/book")
    public String checkout(@PathVariable Long tripId,
                           @RequestParam(defaultValue = "1") int seats,
                           @RequestParam(defaultValue = "10") int advancePercent,
                           @RequestParam String phone,
                           @RequestParam String address,
                           @RequestParam String city,
                           @RequestParam String postcode,
                           Authentication authentication,
                           RedirectAttributes flash) {
        if (!gateway.ready()) {
            flash.addFlashAttribute("bookingError", "Sandbox payment is not yet configured. No seat was reserved.");
            return "redirect:/trips/" + tripId + "/reserve";
        }
        BookingService.Start reservation = null;
        try {
            // Address validation before holding a seat; gateway applies the same checks.
            SandboxPaymentGateway.Billing billing =
                    new SandboxPaymentGateway.Billing(phone, address, city, postcode);
            if (phone == null || !phone.matches("[+]?[0-9]{10,15}") || address.isBlank()
                    || address.length() > 50 || city.isBlank() || city.length() > 50
                    || postcode.isBlank() || !postcode.matches("[A-Za-z0-9 -]{3,15}"))
                throw new IllegalArgumentException("Enter valid billing contact details.");
            reservation = bookings.reserve(authentication.getName(), tripId, seats, advancePercent);
            String url = gateway.start(reservation, billing);
            return "redirect:" + url;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            if (reservation != null) bookings.releaseIfGatewayFails(reservation.transactionId());
            flash.addFlashAttribute("bookingError", ex.getMessage());
        } catch (Exception ex) {
            if (reservation != null) bookings.releaseIfGatewayFails(reservation.transactionId());
            log.warn("Sandbox checkout initialization failed: {}", ex.getClass().getSimpleName());
            flash.addFlashAttribute("bookingError", "Sandbox checkout is temporarily unavailable. No seat was confirmed.");
        }
        return "redirect:/trips/" + tripId + "/reserve";
    }

    @GetMapping("/bookings")
    public String mine(Authentication authentication, Model model) {
        var mine = bookings.myBookings(authentication.getName());
        model.addAttribute("bookings", mine);
        java.util.Map<Long, Boolean> access = new java.util.HashMap<>();
        mine.forEach(b -> access.put(b.getTrip().getId(),
                groups.hasAccess(authentication.getName(), b.getTrip().getId())));
        model.addAttribute("groupAccessByTrip", access);
        return "booking/my-bookings";
    }

    @GetMapping("/bookings/{id}")
    public String receipt(@PathVariable Long id, Authentication authentication, Model model) {
        model.addAttribute("booking", bookings.mine(authentication.getName(), id));
        model.addAttribute("checkoutReady", gateway.ready());
        model.addAttribute("groupAccess", groups.hasAccess(
                authentication.getName(), bookings.mine(authentication.getName(), id).getTrip().getId()));
        return "booking/receipt";
    }

    @PostMapping("/bookings/{id}/pay-balance")
    public String payBalance(@PathVariable Long id,
                             @RequestParam String phone,
                             @RequestParam String address,
                             @RequestParam String city,
                             @RequestParam String postcode,
                             Authentication auth, RedirectAttributes flash) {
        if (!gateway.ready()) {
            flash.addFlashAttribute("balanceError", "Sandbox gateway not configured. No payment was started.");
            return "redirect:/bookings/" + id;
        }
        BookingService.Start reservation = null;
        try {
            SandboxPaymentGateway.Billing billing = new SandboxPaymentGateway.Billing(phone, address, city, postcode);
            if (phone == null || !phone.matches("[+]?[0-9]{10,15}")
                    || address == null || address.isBlank() || address.length() > 50
                    || city == null || city.isBlank() || city.length() > 50
                    || postcode == null || !postcode.matches("[A-Za-z0-9 -]{3,15}"))
                throw new IllegalArgumentException("Enter valid billing contact details.");
            reservation = bookings.reserveBalance(auth.getName(), id);
            return "redirect:" + gateway.start(reservation, billing);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            if (reservation != null) bookings.releaseIfGatewayFails(reservation.transactionId());
            flash.addFlashAttribute("balanceError", ex.getMessage());
        } catch (Exception ex) {
            if (reservation != null) bookings.releaseIfGatewayFails(reservation.transactionId());
            log.warn("Balance sandbox checkout unavailable: {}", ex.getClass().getSimpleName());
            flash.addFlashAttribute("balanceError", "Sandbox checkout is unavailable. Nothing is marked as paid.");
        }
        return "redirect:/bookings/" + id;
    }

    private void handleNotification(String transactionId, String validationId) {
        if (!gateway.ready()) throw new IllegalStateException("Sandbox is disabled.");
        if (transactionId == null) throw new IllegalArgumentException("Missing payment reference.");
        PaymentAttempt attempt = attempts.findByTransactionId(transactionId).orElse(null);
        if (attempt == null) throw new IllegalArgumentException("Unknown payment reference.");
        if ("VERIFIED".equals(attempt.getStatus()) || "REVIEW".equals(attempt.getStatus())) return;
        SandboxPaymentGateway.Notification notification =
                gateway.notification(transactionId, validationId, attempt.getExpectedAmount());
        if (notification.payment() != null) bookings.applyVerified(transactionId, notification.payment());
        else bookings.applyTerminal(transactionId, notification.terminalStatus());
    }

    /** Gateway POSTs here without a browser session; receipt is always revalidated server-side. */
    @RequestMapping(path = "/payments/sslcommerz/success", method = {RequestMethod.GET, RequestMethod.POST})
    public String success(@RequestParam(name = "tran_id", required = false) String reference,
                          @RequestParam(name = "val_id", required = false) String validationId,
                          Model model) {
        try { handleNotification(reference, validationId); }
        catch (Exception ex) {
            log.warn("Sandbox callback verification pending: {}", ex.getClass().getSimpleName());
        }
        model.addAttribute("gatewayMessage", "Payment response received. Sign in to check whether your booking was verified.");
        return "booking/gateway-return";
    }

    @RequestMapping(path = {"/payments/sslcommerz/fail", "/payments/sslcommerz/cancel"},
                    method = {RequestMethod.GET, RequestMethod.POST})
    public String failed(@RequestParam(name = "tran_id", required = false) String reference, Model model) {
        try { handleNotification(reference, null); }
        catch (Exception ex) {
            log.warn("Sandbox return reconciliation pending: {}", ex.getClass().getSimpleName());
        }
        model.addAttribute("gatewayMessage", "The checkout did not complete. No payment is confirmed from this page.");
        return "booking/gateway-return";
    }

    @PostMapping("/payments/sslcommerz/ipn")
    @ResponseBody
    public ResponseEntity<String> ipn(@RequestParam(name = "tran_id", required = false) String reference,
                                       @RequestParam(name = "val_id", required = false) String validationId) {
        try {
            handleNotification(reference, validationId);
            return ResponseEntity.ok("ACK");
        } catch (IllegalArgumentException ex) {
            log.warn("Sandbox IPN not verified: {}", ex.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("NOT VERIFIED");
        } catch (Exception ex) {
            log.warn("Sandbox IPN verification unavailable: {}", ex.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("RETRY");
        }
    }
}
