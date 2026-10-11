package com.mehedi.waylo;
import com.mehedi.waylo.config.JwtConfig;
import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class JwtSecurityTests {
    @Test void signedTokensRequireCurrentEnabledAccountAndVersion() {
        JwtConfig config=new JwtConfig();byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);
        var key=config.wayloJwtKey(Base64.getEncoder().encodeToString(bytes));var users=mock(UserRepository.class);
        User user=new User();user.setEmail("test@example.test");when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var claims=JwtClaimsSet.builder().issuer("waylo").subject(user.getEmail()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claim("role","MEMBER").claim("authVersion",0).build();
        String token=config.jwtEncoder(key).encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        var decoder=config.jwtDecoder(key,users);assertEquals(user.getEmail(),decoder.decode(token).getSubject());
        user.setEnabled(false);assertThrows(JwtException.class,()->decoder.decode(token));
        user.setEnabled(true);user.setAuthVersion(1);assertThrows(JwtException.class,()->decoder.decode(token));
    }
    @Test void expiredOrWronglySignedTokensAreRejected() {
        JwtConfig config=new JwtConfig();var users=mock(UserRepository.class);User u=new User();u.setEmail("test@example.test");when(users.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
        byte[] first=new byte[32],second=new byte[32];Arrays.fill(first,(byte)1);Arrays.fill(second,(byte)2);
        var key=config.wayloJwtKey(Base64.getEncoder().encodeToString(first));
        var other=config.wayloJwtKey(Base64.getEncoder().encodeToString(second));
        var claims=JwtClaimsSet.builder().issuer("waylo").subject(u.getEmail()).issuedAt(Instant.now().minusSeconds(600)).expiresAt(Instant.now().minusSeconds(300)).claim("role","MEMBER").claim("authVersion",0).build();
        String token=config.jwtEncoder(key).encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        assertThrows(JwtException.class,()->config.jwtDecoder(key,users).decode(token));
        assertThrows(JwtException.class,()->config.jwtDecoder(other,users).decode(token));
    }
    @Test void weakSigningKeyIsRejected() { assertThrows(IllegalStateException.class,()->new JwtConfig().wayloJwtKey("dGVzdA==")); }
}
