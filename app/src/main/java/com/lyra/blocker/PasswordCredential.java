// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Password hashing shared by Android and the offline JVM checks. Never stores plaintext. */
public final class PasswordCredential {
    public static final int ITERATIONS = 600_000;
    private PasswordCredential() {}

    public static String problem(String password) {
        int length = password.codePointCount(0, password.length());
        if (length < 12) return "Use at least 12 characters. A few unrelated words work well.";
        if (length > 128) return "Use no more than 128 characters.";
        if (password.codePoints().allMatch(Character::isWhitespace)) return "Use words or characters, not only spaces.";
        if (password.codePoints().distinct().count() < 4) return "Avoid repeating just a few characters.";
        String lower = password.toLowerCase(java.util.Locale.ROOT);
        if (lower.matches("(password|qwerty|letmein|administrator|1234567890)+[0-9!@#$]*"))
            return "Avoid obvious passwords and keyboard patterns. Try unrelated words.";
        return null;
    }

    public static final class Hash {
        public final String salt;
        public final String digest;
        public Hash(String salt, String digest) { this.salt = salt; this.digest = digest; }
    }

    public static Hash create(String password) {
        String problem = problem(password);
        if (problem != null) throw new IllegalArgumentException(problem);
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        return new Hash(hex(salt), hex(derive(password, salt, ITERATIONS)));
    }

    public static boolean verify(String password, String salt, String digest, int iterations) {
        if (password.codePointCount(0, password.length()) > 128 || password.isEmpty()) return false;
        if (iterations != ITERATIONS && iterations != 120_000) return false;
        if (salt == null || digest == null || !salt.matches("[a-f0-9]{64}") || !digest.matches("[a-f0-9]{64}")) return false;
        return MessageDigest.isEqual(derive(password, unhex(salt), iterations), unhex(digest));
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        char[] characters = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(characters, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("Password verification is unavailable", error);
        } finally {
            spec.clearPassword();
            Arrays.fill(characters, '\0');
        }
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
        return result.toString();
    }
    private static byte[] unhex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
}
