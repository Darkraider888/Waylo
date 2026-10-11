package com.mehedi.waylo.booking;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import java.time.*;
@Configuration
@EnableScheduling
public class BookingMaintenance {
    private final BookingRepository bookings;private final BookingService service;
    public BookingMaintenance(BookingRepository bookings,BookingService service){this.bookings=bookings;this.service=service;}
    @Scheduled(initialDelay=60000,fixedDelay=60000)
    public void expire(){
        try { for(Long id:bookings.expiredPendingIds(LocalDateTime.now(ZoneId.of("Asia/Dhaka")),PageRequest.of(0,100)))service.expireHold(id); }
        catch(Exception ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Booking hold maintenance will retry: {}",ex.getClass().getSimpleName());}
    }
}
