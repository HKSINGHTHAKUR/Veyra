package com.healthcare.appointment.integration.email;

import java.util.Set;

/**
 * Centralized notification recipient routing.
 *
 * Seeded Veyra demo accounts route their notification emails to a single demo
 * destination so the recruiter demo is usable end-to-end (their stored email is
 * never changed). Every real user keeps their own registered email as the
 * recipient. The mapping is explicit per account — never a generic domain rule.
 */
public final class Recipients {

    private static final Set<String> DEMO_ACCOUNT_EMAILS = Set.of(
            "patient@healthcare.dev",
            "doctor@healthcare.dev",
            "admin@healthcare.dev"
    );

    private static final String DEMO_DESTINATION = "harshkusingh9456@gmail.com";

    private Recipients() {
    }

    /**
     * Returns the destination email for a notification recipient's registered
     * email. Demo accounts are mapped to the demo destination; all other
     * recipients keep their own email.
     */
    public static String resolve(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) return userEmail;
        return DEMO_ACCOUNT_EMAILS.contains(userEmail) ? DEMO_DESTINATION : userEmail;
    }
}
