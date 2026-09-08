package com.ams.leaseoccupancy.config;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Parses PEM-encoded RSA keys (PKCS8 private / X.509 public) supplied via env vars. */
final class PemUtils {

    private PemUtils() {
    }

    static PublicKey parsePublicKey(String pem) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(new X509EncodedKeySpec(decode(pem)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Invalid RSA public key material", ex);
        }
    }

    static PrivateKey parsePrivateKey(String pem) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decode(pem)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Invalid RSA private key material", ex);
        }
    }

    private static byte[] decode(String pem) {
        String cleaned = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(cleaned);
    }
}
