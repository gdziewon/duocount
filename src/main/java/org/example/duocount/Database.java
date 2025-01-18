package org.example.duocount;
import com.google.gson.JsonObject;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
public class Database {
    private static final String URL = "jdbc:mysql://localhost:3306/duocount";
    private static final String USER = "root";
    private static final String PASSWORD = "zaq1@WSX";
    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("MySQL Driver Loaded Successfully.");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL Driver not found.");
            throw new RuntimeException("Failed to load MySQL driver", e);
        }
    }
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
   
    public static int addUser(String name) {
        String checkQuery = "SELECT id FROM users WHERE name = ?";
        String insertQuery = "INSERT INTO users (name) VALUES (?)";
        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkQuery);
             PreparedStatement insertStmt = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
            checkStmt.setString(1, name);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
            insertStmt.setString(1, name);
            insertStmt.executeUpdate();
            ResultSet keys = insertStmt.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }
   
    public static int addWallet(String name) {
        String checkQuery = "SELECT id FROM wallets WHERE name = ?";
        String insertQuery = "INSERT INTO wallets (name) VALUES (?)";
        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkQuery);
             PreparedStatement insertStmt = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
            checkStmt.setString(1, name);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
            insertStmt.setString(1, name);
            insertStmt.executeUpdate();
            ResultSet keys = insertStmt.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }
   
    public static int addExpense(int walletId, int payerId, String description, double amount) {
        String query = "INSERT INTO expenses (wallet_id, payer_id, description, amount) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, walletId);
            stmt.setInt(2, payerId);
            stmt.setString(3, description);
            stmt.setDouble(4, amount);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static void addExpenseParticipant(int expenseId, int participantId, double share) {
        String query = "INSERT INTO expense_participants (expense_id, participant_id, share) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, expenseId);
            stmt.setInt(2, participantId);
            stmt.setDouble(3, share);
            stmt.executeUpdate();

            System.out.println(String.format("Added share for expense ID %d, participant ID %d, share %.2f",
                    expenseId, participantId, share));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public static HashMap<String, Double> calculateNetBalances(int walletId) {
        HashMap<String, Double> netBalances = new HashMap<>();
        String query = """
        SELECT u.name AS participant,
               COALESCE(SUM(CASE WHEN e.payer_id = u.id THEN e.amount ELSE 0 END), 0) AS paid,
               COALESCE(SUM(ep.share), 0) AS owed
        FROM users u
        LEFT JOIN expense_participants ep ON ep.participant_id = u.id
        LEFT JOIN expenses e ON ep.expense_id = e.id
        WHERE e.wallet_id = ?
        GROUP BY u.id, u.name
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, walletId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String participant = rs.getString("participant");
                    double paid = rs.getDouble("paid");
                    double owed = rs.getDouble("owed");
                    double balance = paid - owed;
                    netBalances.put(participant, balance);
                    System.out.printf("Participant: %s, Paid: %.2f, Owed: %.2f, Balance: %.2f%n", participant, paid, owed, balance);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return netBalances;
    }




    public static List<String> getAllWallets() {
        List<String> wallets = new ArrayList<>();
        String query = "SELECT name FROM wallets";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                wallets.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return wallets;
    }
   
    public static int getWalletIdByName(String name) {
        String query = "SELECT id FROM wallets WHERE name = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static int getUserIdByName(String name) {
        String query = "SELECT id FROM users WHERE name = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1; // User not found
    }

    public static List<JsonObject> getWalletDetails(int walletId) {
        String query = """
        SELECT e.description, e.amount, u.name AS payer
        FROM expenses e
        JOIN users u ON e.payer_id = u.id
        WHERE e.wallet_id = ?
    """;

        List<JsonObject> details = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, walletId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                JsonObject expense = new JsonObject();
                expense.addProperty("description", rs.getString("description"));
                expense.addProperty("amount", rs.getDouble("amount"));
                expense.addProperty("payer", rs.getString("payer"));
                details.add(expense);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return details;
    }


    public static List<String> getParticipantsByWalletId(int walletId) {
        String query = """
        SELECT DISTINCT u.name
        FROM users u
        JOIN expense_participants ep ON u.id = ep.participant_id
        JOIN expenses e ON ep.expense_id = e.id
        WHERE e.wallet_id = ?
    """;
        List<String> participants = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, walletId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    participants.add(rs.getString("name"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return participants;
    }


}
