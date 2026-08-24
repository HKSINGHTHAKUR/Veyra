package com.healthcare.appointment;

import java.sql.*;

public class VerifyCleanup {
    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");
        String url = "jdbc:postgresql://localhost:5432/healthcare_appointments";
        try (Connection conn = DriverManager.getConnection(url, "postgres", "postgres")) {
            try (Statement st = conn.createStatement()) {
                ResultSet rs = st.executeQuery("SELECT count(*) FROM appointments WHERE appointment_start::date = '2026-09-09'");
                rs.next();
                System.out.println("Appointments on 2026-09-09: " + rs.getInt(1));
                
                rs = st.executeQuery("SELECT count(*) FROM slot_holds WHERE slot_start::date = '2026-09-09' AND status = 'ACTIVE'");
                rs.next();
                System.out.println("ACTIVE holds on 2026-09-09: " + rs.getInt(1));
                
                rs = st.executeQuery("SELECT count(*) FROM ai_summaries WHERE appointment_id IN (SELECT id FROM appointments WHERE appointment_start::date = '2026-09-09')");
                rs.next();
                System.out.println("AI summaries for 2026-09-09: " + rs.getInt(1));
                
                rs = st.executeQuery("SELECT count(*) FROM notifications WHERE appointment_id IN (SELECT id FROM appointments WHERE appointment_start::date = '2026-09-09')");
                rs.next();
                System.out.println("Notifications for 2026-09-09: " + rs.getInt(1));
                
                rs = st.executeQuery("SELECT count(*) FROM symptom_submissions WHERE appointment_id IN (SELECT id FROM appointments WHERE appointment_start::date = '2026-09-09')");
                rs.next();
                System.out.println("Symptom submissions for 2026-09-09: " + rs.getInt(1));
            }
        }
    }
}