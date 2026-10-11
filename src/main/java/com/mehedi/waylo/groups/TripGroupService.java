package com.mehedi.waylo.groups;

import com.mehedi.waylo.booking.Booking;
import com.mehedi.waylo.booking.BookingRepository;
import com.mehedi.waylo.entity.Trip;
import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.enums.Role;
import com.mehedi.waylo.enums.TripStatus;
import com.mehedi.waylo.repository.TripRepository;
import com.mehedi.waylo.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
public class TripGroupService {
    private final TripGroupMemberRepository members;
    private final TripGroupMessageRepository messages;
    private final BookingRepository bookings;
    private final TripRepository trips;
    private final UserRepository users;
    private final com.mehedi.waylo.service.AuditService audit;

    public TripGroupService(TripGroupMemberRepository members, TripGroupMessageRepository messages,
                            BookingRepository bookings, TripRepository trips, UserRepository users, com.mehedi.waylo.service.AuditService audit) {
        this.members = members;
        this.messages = messages;
        this.bookings = bookings;
        this.trips = trips;
        this.users = users;
        this.audit = audit;
    }

    private User actor(String email) {
        if (email == null) throw new AccessDeniedException("Sign in first.");
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(User::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable."));
    }

    private Trip trip(Long tripId) {
        Trip trip = trips.findById(tripId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found."));
        if (trip.getStatus() != TripStatus.PUBLISHED && trip.getStatus() != TripStatus.COMPLETED)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group opens after trip publication.");
        return trip;
    }

    private boolean manager(Trip trip, User user) {
        return user.getRole() == Role.ADMIN || trip.getOrganizer().getId().equals(user.getId());
    }

    private void requireAccess(Trip trip, User user) {
        if (manager(trip, user)) return;
        if (!members.existsByTripIdAndUserIdAndActiveTrue(trip.getId(), user.getId())
                || bookings.verifiedGroupBookings(trip.getId(), user.getId()) == 0) {
            throw new AccessDeniedException("A confirmed booking and active group membership are required.");
        }
    }

    /** Used to show a group link only to travelers whose entitlement is still active. */
    @Transactional(readOnly = true)
    public boolean hasAccess(String email, Long tripId) {
        if (email == null || email.isBlank()) return false;
        User user = users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(User::isEnabled).orElse(null);
        if (user == null) return false;
        Trip trip = trips.findById(tripId).orElse(null);
        if (trip == null || (trip.getStatus() != TripStatus.PUBLISHED
                && trip.getStatus() != TripStatus.COMPLETED)) return false;
        if (manager(trip, user)) return true;
        return members.existsByTripIdAndUserIdAndActiveTrue(tripId, user.getId())
                && bookings.verifiedGroupBookings(tripId, user.getId()) > 0;
    }

    public record MessageView(Long id, Long authorId, String authorName,
                              String content, LocalDateTime createdAt, boolean removed) { }

    private MessageView view(TripGroupMessage m) {
        return new MessageView(m.getId(), m.getSender().getId(), m.getSender().getFullName(),
                m.getRemovedAt() != null ? "Message removed by moderator" : m.getContent(),
                m.getCreatedAt(), m.getRemovedAt() != null);
    }

    public record GroupPage(Trip trip, boolean moderator, Long currentUserId,
                            List<MessageView> messages, List<TripGroupMember> members,
                            List<Booking> eligibleBookings) { }

    @Transactional(readOnly = true)
    public GroupPage page(String email, Long tripId, long beforeId) {
        User user = actor(email);
        Trip trip = trip(tripId);
        requireAccess(trip, user);
        boolean moderator = manager(trip, user);
        List<TripGroupMessage> latest = new ArrayList<>(
                beforeId > 0 ? messages.findByTripIdAndIdLessThanOrderByIdDesc(tripId, beforeId, PageRequest.of(0, 70))
                        : messages.findByTripIdOrderByIdDesc(tripId, PageRequest.of(0, 70)));
        Collections.reverse(latest);
        List<MessageView> result = latest.stream().map(this::view).toList();
        List<TripGroupMember> allMembers = members.findByTripIdOrderByJoinedAtAsc(tripId);
        List<TripGroupMember> groupMembers = moderator ? allMembers
                : allMembers.stream().filter(TripGroupMember::isActive).toList();
        List<Booking> eligible = moderator
                ? bookings.findByTripIdAndStatusOrderByCreatedAtAsc(tripId, "CONFIRMED").stream()
                    .filter(b -> bookings.verifiedGroupBookings(tripId, b.getTraveler().getId()) > 0)
                    .toList() : List.of();
        return new GroupPage(trip, moderator, user.getId(), result, groupMembers, eligible);
    }

    @Transactional(readOnly = true)
    public List<MessageView> updates(String email, Long tripId, long afterId) {
        User user = actor(email);
        Trip trip = trip(tripId);
        requireAccess(trip, user);
        List<TripGroupMessage> snapshot = new ArrayList<>(afterId > 0
                ? messages.findByTripIdAndIdLessThanOrderByIdDesc(tripId, afterId, PageRequest.of(0,70))
                : messages.findByTripIdOrderByIdDesc(tripId, PageRequest.of(0,70)));
        Collections.reverse(snapshot);
        return snapshot.stream().map(this::view).toList();
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void send(String email, Long tripId, String content) {
        User user = actor(email);
        Trip trip = trip(tripId);
        requireAccess(trip, user);
        String text = content == null ? "" : content.strip();
        if (text.isEmpty() || text.length() > 1500)
            throw new IllegalArgumentException("Write a message between 1 and 1,500 characters.");
        users.lockById(user.getId()).orElseThrow();
        if (messages.existsByTripIdAndSenderIdAndCreatedAtAfter(
                tripId, user.getId(), LocalDateTime.now(java.time.ZoneId.of("Asia/Dhaka")).minusSeconds(2)))
            throw new IllegalArgumentException("Wait a moment before sending another message.");
        TripGroupMessage message = new TripGroupMessage();
        message.setTrip(trip);
        message.setSender(user);
        message.setContent(text);
        message.setCreatedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Dhaka")));
        messages.save(message);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void removeMessage(String email, Long tripId, Long messageId) {
        User user = actor(email);
        Trip trip = trip(tripId);
        requireAccess(trip, user);
        TripGroupMessage message = messages.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!message.getTrip().getId().equals(tripId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!manager(trip, user) && !message.getSender().getId().equals(user.getId()))
            throw new AccessDeniedException("Only the author or group moderator can remove this message.");
        if (message.getRemovedAt() == null) {
            message.setRemovedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Dhaka")));
            message.setRemovedBy(user);
            audit.record(user.getId(), "MESSAGE_REMOVED", "MESSAGE", messageId, "Removed by author or group moderator");
        }
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void editMessage(String email, Long tripId, Long messageId, String content) {
        User user = actor(email); Trip trip = trip(tripId); requireAccess(trip,user);
        TripGroupMessage message = messages.findById(messageId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!message.getTrip().getId().equals(tripId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!message.getSender().getId().equals(user.getId())) throw new AccessDeniedException("Only the author can edit a message.");
        if (message.getRemovedAt()!=null || message.getCreatedAt().isBefore(LocalDateTime.now(java.time.ZoneId.of("Asia/Dhaka")).minusMinutes(15)))
            throw new IllegalArgumentException("Messages can be edited for 15 minutes after sending.");
        if (content==null || content.isBlank() || content.strip().length()>1500)
            throw new IllegalArgumentException("Message must be 1–1500 characters.");
        message.setContent(content.strip()); message.setEditedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Dhaka")));
        audit.record(user.getId(),"MESSAGE_EDITED","MESSAGE",messageId,"Author edited within 15 minutes");
    }

    // Confirmed checkout is the *only* automatic access-granting event.
    // Never reactivate an explicitly removed member from a duplicate callback or balance payment.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void grantFromConfirmedBooking(Booking booking) {
        if (!"CONFIRMED".equals(booking.getStatus())) return;
        Long tripId = booking.getTrip().getId();
        Long userId = booking.getTraveler().getId();
        if (members.findByTripIdAndUserId(tripId, userId).isPresent()) return;
        TripGroupMember member = new TripGroupMember();
        member.setTrip(booking.getTrip());
        member.setUser(booking.getTraveler());
        member.setBooking(booking);
        member.setJoinedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Dhaka")));
        member.setActive(true);
        members.saveAndFlush(member);
    }

    private Trip managerTrip(String email, Long tripId) {
        User user = actor(email);
        Trip trip = trip(tripId);
        if (!manager(trip, user)) throw new AccessDeniedException("Group moderator access required.");
        return trip;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void setMembership(String email, Long tripId, Long userId,
                              boolean active, String reason) {
        Trip trip = managerTrip(email, tripId);
        User admin = actor(email);
        User memberUser = users.findById(userId).filter(User::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (manager(trip, memberUser) && memberUser.getId().equals(trip.getOrganizer().getId()))
            throw new IllegalArgumentException("The trip organizer cannot be removed.");
        if (bookings.verifiedGroupBookings(tripId, userId) == 0)
            throw new IllegalArgumentException("Only travelers with server-verified confirmed bookings can be added.");
        trips.lockTripForBooking(tripId).orElseThrow();
        TripGroupMember membership = members.findByTripIdAndUserId(tripId, userId).orElse(null);
        if (membership == null) {
            if (!active) throw new IllegalArgumentException("This traveler is not in the group.");
            membership = new TripGroupMember();
            membership.setTrip(trip);
            membership.setUser(memberUser);
            membership.setJoinedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Dhaka")));
        }
        if (active && (reason == null || reason.strip().length() < 10 || reason.length() > 450))
            throw new IllegalArgumentException("Give an access restoration reason (10–500 characters).");
        if (!active) {
            if (reason == null || reason.strip().length() < 10 || reason.length() > 450)
                throw new IllegalArgumentException("Please provide a removal reason (10–500 characters).");
            membership.setActive(false);
            membership.setRemovedBy(admin);
            membership.setRemovedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Dhaka")));
            membership.setRemovalReason(reason.strip());
        } else {
            membership.setActive(true);
            // Keep the last removal reason/reviewer for moderator reference.
            // Every restoration is recorded in the append-only audit table.
        }
        members.save(membership);
        audit.record(admin.getId(), active ? "GROUP_ACCESS_RESTORED" : "GROUP_ACCESS_REMOVED", "TRIP", tripId, "User #" + userId + ": " + reason.strip());
        // These actions manage chat access only; confirmed bookings/payments are not changed.
    }
}
