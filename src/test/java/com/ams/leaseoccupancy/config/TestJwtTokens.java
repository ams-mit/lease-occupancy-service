package com.ams.leaseoccupancy.config;

import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Mints test JWTs signed with the same private key whose public half is configured as
 * {@code app.jwt.gateway-public-key} in src/test/resources/application.yml — i.e. this
 * class plays the role of the Gateway for tests. Never used outside test sources.
 */
public final class TestJwtTokens {

    // Matches the public key in src/test/resources/application.yml (app.jwt.gateway-public-key).
    private static final String TEST_PRIVATE_KEY_BASE64 = """
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCmFSroUYUYLOyj
            uAcoNpTbbvh2znSpgx5mNJFNQwk6u3Ayf5wpt8ly5U72s339V94+RT+Qq8zPELJV
            iUFZkZwmS604BC7BBS3b4WYBh48qFqDi74ZeZuXPn/bHd7XBeqdZAdzYhj39otR3
            2fLf7V/wkm4Nxf+76w7BrosXAuivDPQmjMsBp+rffcpKrNLEz3XUOF4yyskPDrXP
            TnE+yWTvp7VnB8sP84J/Hed4A959VoesneYAN/mzGMdsnvrQaX2QuglXB+yLePMU
            6n+EwjCZUA05RTZHNsebHNqT/FeSd/LB49MonVtRGtDyYvWAekV6V7n62Y/x2agE
            6NeiSqC/AgMBAAECggEAH9txloyEhiWf4qubG9iUZx77I/boI9fVze8JbBOOwqhP
            ljAqKUpqptJbejDl2uGu7Kzly39Y0uM2AfhJA8BNfn8Ho/YRbc7rhADRSzvQd1dN
            1xnw4UzFijT55jsNLyay0Pztes8NNwizzqWM0+05ePDtkVDIhChLdVVgsrJJJcJ7
            z7Yd3HbXZmosUZjXN5wGKyFZU52i0/Mp87Kr3ty6GmAIEMSHtUTHMjmfgn38Peis
            6OYIGz8mjeH4V/YlE/NBRwi5SMaiVk1VxlvMJKNhcbgdSaVNRXEmhLnFKhE54ScX
            VxFyAk2DrPCfjqOlOwH+M9m1Kcjvw8kPanaMqUf3SQKBgQDkosw8Xqzp4k11IqCA
            GNvlBeU/YfcrdCPYMxOV4oFcQk6HtMcs/nWJACWyHgu2LoZ4aEwzCLMnMyjDuxS5
            6aM8a7+BiKRyFNfjVE0NvkP+WhvrHbnRKEw9vbyc5NMkVU3jvouqvzlmUfbAoGhd
            oThrISCEKvrRwQBSEoetaE1cawKBgQC59cs8nh6VRuAp6EOIz6+YEqgnCyvTK/kF
            WAmVf/z99qMH7K+X5uu1YJG9kOwwSvcu5r8I+uoZH2Espcems2inxlh29GkTheOC
            PzVaJNSKE4LCgH+1Iy8gXBOxlLhXlN/CcJ+tAkuprhro+JNJo6/git2gkng3uxXq
            XyqgceWh/QKBgQCgT4Xkzn1vgL3O4/il2MhWTUvwpUApt8VqUmXpMmiBx/xIKvl9
            Eu5WoUPNIEQV+YlP49GJ9w+m7LgohYSC1s8eDm645JZpZzP4saNVf5MtRQFOWglw
            HlFW+TLGz6s8TpPOnExR7MLSe6YIanDDNcfZVeD2yywS5sDSyytW60tTAQKBgGIC
            buxy9fF9WnYb6UQ5GfYPlzqEw8NXwLaVUO/PdhqwG/r4Pw/TcFW3IPkFYcaOLDs2
            +GaWQD2fcUrj0RdGJZmPi16esKbOgTtLbyYklTR01g0HRsfEVHk8rlCA7quSVmCX
            lUDNHe2/SoWRRaehMvgomGih6eSNoR5WrBPGE95NAoGAIlx4WZtq20z5N9QF3Zcf
            Iibk+m38+681uRhNLkMWPh2D9KcLR3gZl9dMbOyGr3WuM+OUslWFvt2+TBk35Gs3
            T0NdO1QrW+pAKGphMqpBeCLx+j7c01AszZ9ijcXsmNV+XXy/I9Zoj1o0hssdgBHA
            t5H4B3oHJ7PmqFgwifHpHAA=
            """.replace("\n", "");

    private static final PrivateKey TEST_PRIVATE_KEY = loadPrivateKey();

    private TestJwtTokens() {
    }

    public static String userToken(String userId, String... roles) {
        return token(userId, "user", List.of(roles));
    }

    public static String serviceToken(String serviceName) {
        return token(serviceName, "service", null);
    }

    public static String expiredUserToken(String userId, String... roles) {
        Instant past = Instant.now().minusSeconds(3600);
        var builder = Jwts.builder()
                .subject(userId)
                .claim("type", "user")
                .claim("roles", List.of(roles))
                .issuedAt(Date.from(past.minusSeconds(600)))
                .expiration(Date.from(past));
        return builder.signWith(TEST_PRIVATE_KEY, Jwts.SIG.RS256).compact();
    }

    private static String token(String subject, String type, List<String> roles) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(subject)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)));
        if (roles != null) {
            builder.claim("roles", roles);
        }
        return builder.signWith(TEST_PRIVATE_KEY, Jwts.SIG.RS256).compact();
    }

    private static PrivateKey loadPrivateKey() {
        try {
            byte[] der = Base64.getDecoder().decode(TEST_PRIVATE_KEY_BASE64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load test JWT signing key", ex);
        }
    }
}
