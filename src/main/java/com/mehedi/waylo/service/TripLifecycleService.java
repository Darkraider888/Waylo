package com.mehedi.waylo.service;
import com.mehedi.waylo.enums.*;
import com.mehedi.waylo.repository.*;
import com.mehedi.waylo.booking.BookingRepository;
import com.mehedi.waylo.tripscope.TripProposalService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
@Service
public class TripLifecycleService {
    private final TripRepository trips;private final UserRepository users;private final BookingRepository bookings;
    private final TripProposalService proposals;private final AuditService audit;private final NotificationService notifications;
    public TripLifecycleService(TripRepository trips,UserRepository users,BookingRepository bookings,TripProposalService proposals,AuditService audit,NotificationService notifications){
        this.trips=trips;this.users=users;this.bookings=bookings;this.proposals=proposals;this.audit=audit;this.notifications=notifications;
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void change(String email,Long id,boolean cancel,String reason){
        var user=users.findByEmail(email).filter(com.mehedi.waylo.entity.User::isEnabled).orElseThrow(()->new AccessDeniedException("Account unavailable."));
        var trip=trips.lockTripForBooking(id).orElseThrow(()->new IllegalArgumentException("Trip not found."));
        boolean admin=user.getRole()==Role.ADMIN;
        if(!admin && (!trip.getOrganizer().getId().equals(user.getId()) || (user.getRole()!=Role.ORGANIZER && !proposals.approvedForTrip(user.getId(),id))))
            throw new AccessDeniedException("Only this trip's approved organizer or an admin can manage its status.");
        if(reason==null||reason.strip().length()<10||reason.length()>450)throw new IllegalArgumentException("Give a reason between 10 and 450 characters.");
        if(trip.getStatus()==TripStatus.CANCELLED||trip.getStatus()==TripStatus.COMPLETED)throw new IllegalArgumentException("This trip is already closed.");
        if(!cancel && (trip.getStatus()!=TripStatus.PUBLISHED||trip.getDepartureDate().isAfter(LocalDateTime.now(ZoneId.of("Asia/Dhaka")))))
            throw new IllegalArgumentException("Only published trips whose departure has passed can be completed.");
        trip.setStatus(cancel?TripStatus.CANCELLED:TripStatus.COMPLETED);
        audit.record(user.getId(),cancel?"TRIP_CANCELLED":"TRIP_COMPLETED","TRIP",id,reason.strip());
        for(var booking:bookings.findByTripIdAndStatusOrderByCreatedAtAsc(id,"CONFIRMED"))
            notifications.send(booking.getTraveler().getId(),cancel?"Trip cancelled. Your payment record is preserved; contact the organizer about reconciliation. No refund has been issued automatically.":"Your trip has been marked completed.","/bookings/"+booking.getId());
        if(cancel) for(var booking:bookings.findByTripIdAndStatusOrderByCreatedAtAsc(id,"PENDING_PAYMENT"))booking.setStatus("CANCELLED");
    }
}
