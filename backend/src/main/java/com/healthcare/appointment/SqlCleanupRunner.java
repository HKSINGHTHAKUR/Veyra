package com.healthcare.appointment;

import java.sql.*;

public class SqlCleanupRunner {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/healthcare_appointments";
    
    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection(DB_URL, "postgres", "postgres")) {
            executeCleanup(conn);
        }
    }
    
    public static void executeCleanup(Connection conn) throws SQLException {
        String[] sqlStatements = {
            "DELETE FROM ai_summaries WHERE appointment_id IN (SELECT id FROM appointments WHERE appointment_start::date = '2026-09-09')",
            "DELETE FROM notifications WHERE appointment_id IN (SELECT id FROM appointments WHERE appointment_start::date = '2026-09-09')",
            "DELETE FROM symptom_submissions WHERE appointment_id IN (SELECT id FROM appointments WHERE appointment_start::date = '2026-09-09')",
            "DELETE FROM appointments WHERE appointment_start::date = '2026-09-09'",
            "DELETE FROM slot_holds WHERE slot_start::date = '2026-09-09' AND status = 'ACTIVE'"
        };
        
        for (String sql : sqlStatements) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int affected = ps.executeUpdate();
                System.out.println("Executed: " + sql.substring(0, Math.min(60, sql.length())) + "...");
                System.out.println("  Affected rows: " + affected);
            }
        }
        
        try (Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery(
                "SELECT count(*) FROM appointments WHERE appointment_start::date = '2026-09-09'");
            rs.next();
            System.out.println("Remaining appointments on 2026-09-09: " + rs.getInt(1));
            
            rs = st.executeQuery(
                "SELECT count(*) FROM slot_holds WHERE slot_start::date = '2026-09-09' AND status = 'ACTIVE'");
            rs.next();
            System.out.println("Remaining ACTIVE holds on 2026-09-09: " + rs.getInt(1));
        }
    }
}