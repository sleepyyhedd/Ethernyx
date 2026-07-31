package com.ethernyx.auth.util;

import org.mindrot.jbcrypt.BCrypt;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public final class PasswordUtil {

    private PasswordUtil() {}

    public static String hash(String plainPassword, int strength) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(strength));
    }

    public static boolean matches(String plainPassword, String hash) {
        if (hash == null || hash.isEmpty()) return false;
        try {
            return BCrypt.checkpw(plainPassword, hash);
        } catch (IllegalArgumentException ex) {
            // Malformed hash in storage; treat as no match rather than crashing.
            return false;
        }
    }

    /**
     * Validates a password against configured requirements.
     * Returns null if valid, or an error message describing the violated rule.
     */
    public static String validate(String password, FileConfiguration config) {
        int minLength = config.getInt("authentication.password.min-length", 6);
        int maxLength = config.getInt("authentication.password.max-length", 32);

        if (password == null || password.isEmpty()) {
            return "Password cannot be empty.";
        }
        if (password.length() < minLength) {
            return "Password must be at least " + minLength + " characters long.";
        }
        if (password.length() > maxLength) {
            return "Password must be at most " + maxLength + " characters long.";
        }
        if (config.getBoolean("authentication.password.require-uppercase", false) && !password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter.";
        }
        if (config.getBoolean("authentication.password.require-lowercase", false) && !password.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter.";
        }
        if (config.getBoolean("authentication.password.require-number", false) && !password.matches(".*\\d.*")) {
            return "Password must contain at least one number.";
        }
        if (config.getBoolean("authentication.password.require-special", false) && !password.matches(".*[^a-zA-Z0-9].*")) {
            return "Password must contain at least one special character.";
        }
        if (config.getBoolean("authentication.password.disallow-common-passwords", true)) {
            List<String> common = config.getStringList("authentication.password.common-passwords-list");
            for (String c : common) {
                if (password.equalsIgnoreCase(c)) {
                    return "That password is too common. Please choose another.";
                }
            }
        }
        // Reject characters that would break YAML/command parsing in unexpected ways.
        if (password.contains("\"") || password.contains("'") || password.contains("\\")) {
            return "Password may not contain quote or backslash characters.";
        }
        return null;
    }
}
