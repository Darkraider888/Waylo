
package com.mehedi.waylo;

import org.junit.jupiter.api.Test;
import com.mehedi.waylo.booking.SandboxPaymentGateway;
import com.mehedi.waylo.service.MediaStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.mysql.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import com.mehedi.waylo.entity.*;
import com.mehedi.waylo.enums.*;
import com.mehedi.waylo.repository.*;
import com.mehedi.waylo.service.*;
import com.mehedi.waylo.booking.*;
import com.mehedi.waylo.groups.*;
import com.mehedi.waylo.tripscope.*;
import com.mehedi.waylo.dto.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Requires Docker; never falls back to a development database. */
@SpringBootTest(classes = WayloApplication.class, properties = {"waylo.jwt.enabled=false", "waylo.sslcommerz.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false", "spring.mail.host=localhost",
        "spring.mail.username=test", "spring.mail.password=test"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class WayloApplicationTests {
    static MySQLContainer mysql;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry properties) {
        String external = System.getenv("WAYLO_TEST_DB_URL");
        String url, username, password;
        if (external == null || external.isBlank()) {
            mysql = new MySQLContainer("mysql:8.4").withDatabaseName("waylo_test")
                    .withUsername("waylo_test").withPassword("isolated-test-only");
            mysql.start();
            url = mysql.getJdbcUrl(); username = mysql.getUsername(); password = mysql.getPassword();
        } else {
            // A deliberate local-only alternative for machines without Docker.
            if (!external.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/waylo_test(?:_[a-z0-9]+)?"))
                throw new IllegalArgumentException("Tests require a separate loopback waylo_test database.");
            url = external;
            username = System.getenv("WAYLO_TEST_DB_USERNAME");
            password = System.getenv("WAYLO_TEST_DB_PASSWORD");
        }
        final String jdbcUrl = url, jdbcUser = username, jdbcPassword = password;
        properties.add("spring.datasource.url", () -> jdbcUrl);
        properties.add("spring.datasource.username", () -> jdbcUser);
        properties.add("spring.datasource.password", () -> jdbcPassword);
        properties.add("spring.flyway.url", () -> jdbcUrl);
        properties.add("spring.flyway.user", () -> jdbcUser);
        properties.add("spring.flyway.password", () -> jdbcPassword);
    }

    @org.junit.jupiter.api.AfterAll
    static void stopContainer() { if (mysql != null) mysql.stop(); }

    @MockitoBean MediaStorageService media;
    @MockitoBean SandboxPaymentGateway payments;
    @MockitoBean JavaMailSender mail;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired DestinationRepository destinations;
    @Autowired BookingRepository bookings;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired BookingService bookingService;
    @Autowired SocialService social;
    @Autowired ModerationService moderation;
    @Autowired OrganizerTripService organizers;
    @Autowired TripProposalService proposals;
    @Autowired TripGroupService groups;
    @Autowired DestinationService destinationService;
    @Autowired TripLifecycleService lifecycle;
    @Autowired AuthService authService;
    @Autowired PasswordResetService resetService;
    @Autowired OrganizerApplicationService globalApplications;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

    User account(Role role) {
        User u=new User();u.setFullName("Isolated Test "+role);u.setEmail(UUID.randomUUID()+"@example.test");
        u.setPassword(encoder.encode("TestPassword123"));u.setRole(role);return users.saveAndFlush(u);
    }
    Trip trip(User owner,int capacity) {
        Trip t=new Trip();t.setOrganizer(owner);t.setDestination(destinations.findAll().getFirst());t.setTitle("Isolated mountain journey");
        t.setDescription("A test itinerary with enough detail for validation.");t.setDepartureDate(LocalDateTime.now(ZoneId.of("Asia/Dhaka")).plusDays(3));
        t.setPrice(new BigDecimal("1000.00"));t.setCapacity(capacity);t.setStatus(TripStatus.PUBLISHED);return trips.saveAndFlush(t);
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor as(User u) { return user(new com.mehedi.waylo.config.WayloPrincipal(u)); }

    @Test
    void contextLoads() {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class).startsWith("waylo_test"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0", Integer.class));
        assertTrue(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class) >= 16);
    }

    @Test void guestsCanBrowseButCannotChangeContentOrAccessPrivatePages() throws Exception {
        for(String path:List.of("/feed","/trips","/search?q=tea","/login","/register","/forgot-password","/healthz"))
            mvc.perform(get(path)).andExpect(status().isOk());
        for(String path:List.of("/bookings","/posts/new","/groups/trips/1","/admin/moderation"))
            mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        mvc.perform(post("/posts/1/like")).andExpect(status().isForbidden());
    }

    @Test void memberCannotEnterAdminAndSessionRoleChangeRequiresNewLogin() throws Exception {
        User member=account(Role.MEMBER);
        mvc.perform(get("/admin/moderation").with(as(member))).andExpect(status().isForbidden());
        var oldPrincipal=as(member);member.setAuthVersion(1);users.saveAndFlush(member);
        mvc.perform(get("/bookings").with(oldPrincipal)).andExpect(status().is3xxRedirection());
    }

    @Test void socialEditingAndModerationRespectOwnershipAndVisibility() throws Exception {
        User author=account(Role.MEMBER),other=account(Role.MEMBER),admin=account(Role.ADMIN);
        CreatePostRequest request=new CreatePostRequest();request.setCaption("A real test story with <script> markup");
        Post post=social.createPost(author.getEmail(),request,null);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->social.editPost(post.getId(),other.getEmail(),"Stolen",null));
        social.editPost(post.getId(),author.getEmail(),"Updated <script>alert(1)</script>",null);
        social.comment(post.getId(),author.getEmail(),"Own comment");
        Long commentId=jdbc.queryForObject("SELECT id FROM post_comments WHERE post_id=?",Long.class,post.getId());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->social.changeComment(post.getId(),commentId,other.getEmail(),"bad",false));
        social.changeComment(post.getId(),commentId,author.getEmail(),"Edited safely",false);
        mvc.perform(get("/posts/"+post.getId()).with(as(author))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
        moderation.report(other.getEmail(),"POST",post.getId(),"This test story needs moderator review.");
        Long reportId=jdbc.queryForObject("SELECT id FROM content_reports WHERE target_type='POST' AND target_id=?",Long.class,post.getId());
        moderation.review(admin.getEmail(),reportId,true,"Hidden after reviewing the submitted test report.");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->social.post(post.getId(),author.getEmail()));
        assertTrue(jdbc.queryForObject("SELECT hidden FROM posts WHERE id=?",Boolean.class,post.getId()));
        mvc.perform(get("/admin/moderation").with(as(admin))).andExpect(status().isOk());
    }

    @Test void singleTripApprovalDoesNotGrantGlobalOrganizerRole() {
        User member=account(Role.MEMBER),admin=account(Role.ADMIN);
        TripProposalRequest request=new TripProposalRequest();request.setTitle("Test approved mountain journey");
        request.setDescription("Detailed test itinerary with transport and meeting arrangements.");request.setMotivation("I can organize this test journey with a clear safe travel plan.");
        request.setDestinationId(destinations.findAll().getFirst().getId());request.setDepartureDate(LocalDateTime.now(ZoneId.of("Asia/Dhaka")).plusDays(4));
        request.setPrice(new BigDecimal("1000.00"));request.setCapacity(4);request.setDocumentType(DocumentType.values()[0]);
        proposals.submit(member.getEmail(),request);
        Long proposal=proposals.mine(member.getEmail()).getFirst().getId();
        Long tripId=proposals.review(proposal,admin.getEmail(),true,null,true);
        assertEquals(Role.MEMBER,users.findById(member.getId()).orElseThrow().getRole());
        organizers.publish(member.getEmail(),tripId);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->organizers.requireGlobalManager(member.getEmail()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->organizers.editForm(account(Role.MEMBER).getEmail(),tripId));
    }

    @Test void destinationNormalizationAndDatabaseIdentityRejectDuplicates() {
        User admin=account(Role.ADMIN);DestinationRequest request=new DestinationRequest();
        request.setName("Place "+UUID.randomUUID());request.setDistrict("Test District");request.setCountry("Bangladesh");
        Destination d=destinationService.add(admin.getEmail(),request);
        request.setName("  "+request.getName().toUpperCase(Locale.ROOT)+"  ");
        assertThrows(IllegalArgumentException.class,()->destinationService.add(admin.getEmail(),request));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM destination_keys WHERE destination_id=?",Integer.class,d.getId()));
    }

    @Test void concurrentReservationsCannotOversellLastSeat() throws Exception {
        Trip trip=trip(account(Role.ORGANIZER),1);User first=account(Role.MEMBER),second=account(Role.MEMBER);
        var barrier=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results=new ArrayList<>();
            for(User traveler:List.of(first,second))results.add(executor.submit(()->{
                barrier.await(10,TimeUnit.SECONDS);
                try{bookingService.reserve(traveler.getEmail(),trip.getId(),1,10);return true;}
                catch(IllegalArgumentException ex){return false;}
            }));
            int successes=0;for(var result:results)if(result.get(15,TimeUnit.SECONDS))successes++;
            assertEquals(1,successes);
        }
        assertEquals(1L,bookings.heldSeats(trip.getId(),LocalDateTime.now(ZoneId.of("Asia/Dhaka"))));
    }

    @Test void concurrentCallbacksGrantOneMembershipAndRemovalBlocksEveryGroupRead() throws Exception {
        User organizer=account(Role.ORGANIZER),member=account(Role.MEMBER),outsider=account(Role.MEMBER);Trip trip=trip(organizer,2);
        var start=bookingService.reserve(member.getEmail(),trip.getId(),1,10);
        var verified=new SandboxPaymentGateway.Verification(start.transactionId(),"val"+UUID.randomUUID().toString().replace("-",""),start.payableAmount(),"test-bank");
        var barrier=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)) {
            List<Future<?>> results=new ArrayList<>();for(int i=0;i<2;i++)results.add(executor.submit(()->{barrier.await(10,TimeUnit.SECONDS);bookingService.applyVerified(start.transactionId(),verified);return null;}));
            for(var result:results)result.get(15,TimeUnit.SECONDS);
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM trip_group_members WHERE trip_id=? AND user_id=?",Integer.class,trip.getId(),member.getId()));
        assertTrue(groups.hasAccess(member.getEmail(),trip.getId()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->groups.page(outsider.getEmail(),trip.getId(),0));
        groups.send(member.getEmail(),trip.getId(),"Meeting point for our isolated test trip?");
        mvc.perform(get("/groups/trips/"+trip.getId()).with(as(member))).andExpect(status().isOk());
        groups.setMembership(organizer.getEmail(),trip.getId(),member.getId(),false,"Removed for isolated authorization test.");
        assertFalse(groups.hasAccess(member.getEmail(),trip.getId()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->groups.updates(member.getEmail(),trip.getId(),0));
        assertEquals("CONFIRMED",bookingService.mine(member.getEmail(),start.bookingId()).getStatus());
        groups.setMembership(organizer.getEmail(),trip.getId(),member.getId(),true,"Restored after isolated test review.");
        assertTrue(groups.hasAccess(member.getEmail(),trip.getId()));
        lifecycle.change(organizer.getEmail(),trip.getId(),true,"Cancelled for isolated lifecycle test.");
        assertFalse(groups.hasAccess(member.getEmail(),trip.getId()));
        assertEquals("CONFIRMED",bookingService.mine(member.getEmail(),start.bookingId()).getStatus());
    }

    @Test void bookingReceiptsAreOwnerOnlyAndBalanceNeedsCsrf() throws Exception {
        User owner=account(Role.MEMBER),other=account(Role.MEMBER);Trip trip=trip(account(Role.ORGANIZER),2);
        var start=bookingService.reserve(owner.getEmail(),trip.getId(),1,10);
        mvc.perform(get("/bookings/"+start.bookingId()).with(as(owner))).andExpect(status().isOk());
        mvc.perform(get("/bookings/"+start.bookingId()).with(as(other))).andExpect(status().isNotFound());
        mvc.perform(post("/bookings/"+start.bookingId()+"/pay-balance").with(as(owner))).andExpect(status().isForbidden());
    }

    @Test void expiredHoldReleasesSeatsAndLateVerifiedPaymentIsReconciled() {
        User member=account(Role.MEMBER);Trip trip=trip(account(Role.ORGANIZER),1);
        var start=bookingService.reserve(member.getEmail(),trip.getId(),1,10);
        jdbc.update("UPDATE bookings SET hold_expires_at=? WHERE id=?",LocalDateTime.now(ZoneId.of("Asia/Dhaka")).minusMinutes(1),start.bookingId());
        bookingService.expireHold(start.bookingId());assertEquals("EXPIRED",bookingService.mine(member.getEmail(),start.bookingId()).getStatus());
        assertEquals(1,bookingService.availableSeats(trip));
        bookingService.applyVerified(start.transactionId(),new SandboxPaymentGateway.Verification(start.transactionId(),"val"+UUID.randomUUID().toString().replace("-",""),start.payableAmount(),"test-bank"));
        assertEquals("CONFIRMED",bookingService.mine(member.getEmail(),start.bookingId()).getStatus());
    }

    @Test void registerLoginAndOneTimePasswordResetUseBcryptAndInvalidateTokens() throws Exception {
        RegisterRequest request=new RegisterRequest();request.setFullName("Isolated Auth Traveler");
        request.setEmail(UUID.randomUUID()+"@example.test");request.setPassword("TestPassword123");request.setConfirmPassword("TestPassword123");
        authService.register(request);User member=users.findByEmail(request.getEmail()).orElseThrow();
        assertNotEquals(request.getPassword(),member.getPassword());assertTrue(encoder.matches(request.getPassword(),member.getPassword()));
        mvc.perform(post("/login").with(csrf()).param("email",request.getEmail()).param("password",request.getPassword()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/feed"));
        resetService.requestReset(request.getEmail());
        var captor=org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
        verify(mail).send(captor.capture());
        var matcher=java.util.regex.Pattern.compile("token=([A-Za-z0-9_-]{43})").matcher(captor.getValue().getText());
        assertTrue(matcher.find());String token=matcher.group(1);
        assertTrue(resetService.isTokenValid(token));resetService.resetPassword(token,"ChangedPassword123");
        assertFalse(resetService.isTokenValid(token));
        User changed=users.findById(member.getId()).orElseThrow();assertEquals(1,changed.getAuthVersion());
        assertTrue(encoder.matches("ChangedPassword123",changed.getPassword()));
        assertThrows(IllegalArgumentException.class,()->resetService.resetPassword(token,"AnotherPassword123"));
    }

    @Test void globalApprovalAndOwnedCloudCoverReplacementRenderAllManagerPages() throws Exception {
        User member=account(Role.MEMBER),admin=account(Role.ADMIN),other=account(Role.ORGANIZER);
        OrganizerApplicationRequest request=new OrganizerApplicationRequest();request.setOrganizationName("Isolated travel club");
        request.setExperience("Organizing university travel with clear safety and planning details.");request.setDocumentType(DocumentType.values()[0]);
        globalApplications.apply(member.getEmail(),request);
        Long application=globalApplications.latestApplication(member.getEmail()).getId();
        globalApplications.review(application,admin.getEmail(),true,null);
        member=users.findById(member.getId()).orElseThrow();assertEquals(Role.ORGANIZER,member.getRole());
        Trip trip=trip(member,4);User owner=member;
        var photo=new org.springframework.mock.web.MockMultipartFile("coverPhoto","test.png","image/png",new byte[]{1});
        when(media.store(photo,MediaStorageService.PhotoKind.TRIP)).thenReturn(new MediaStorageService.StoredPhoto(
                "https://res.cloudinary.com/test/cover.jpg","waylo/trips/00000000-0000-0000-0000-000000000001"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->organizers.replaceCover(other.getEmail(),trip.getId(),photo));
        organizers.replaceCover(owner.getEmail(),trip.getId(),photo);
        mvc.perform(get("/trips/"+trip.getId())).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("https://res.cloudinary.com/test/cover.jpg")));
        for(String path:List.of("/organizer/trips","/organizer/trips/new","/profile/"+member.getId(),"/profile/edit","/saved","/notifications","/trip-proposals","/posts/new","/trips/"+trip.getId()+"/reserve"))
            mvc.perform(get(path).with(as(member))).andExpect(status().isOk());
        for(String path:List.of("/admin/dashboard","/admin/destinations","/admin/trips","/admin/payment-reviews","/admin/moderation"))
            mvc.perform(get(path).with(as(admin))).andExpect(status().isOk());
    }
}
