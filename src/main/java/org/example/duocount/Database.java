package org.example.duocount;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {
    private static final String URL = "jdbc:mysql://localhost:3306/duocount";
    private static final String USER = "root";
    private static final String PASSWORD = "zaq1@WSX";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load MySQL driver", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // Add a user
    public static void addUser(String name) {
        String query = "INSERT INTO users (name) VALUES (?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Add a wallet
    public static void addWallet(String name) {
        String query = "INSERT INTO wallets (name) VALUES (?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Add a user to a wallet
    public static void addUserToWallet(int walletId, int userId) {
        String query = "INSERT INTO wallet_users (wallet_id, user_id) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, walletId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Add an expense to a wallet
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

    // Add a participant to an expense
    public static void addExpenseParticipant(int expenseId, int participantId, double share) {
        String query = "INSERT INTO expense_participants (expense_id, participant_id, share) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, expenseId);
            stmt.setInt(2, participantId);
            stmt.setDouble(3, share);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Retrieve all expenses for a wallet
    public static List<String> getExpensesForWallet(int walletId) {
        String query = "SELECT description, amount FROM expenses WHERE wallet_id = ?";
        List<String> expenses = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, walletId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                expenses.add(rs.getString("description") + ": $" + rs.getDouble("amount"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return expenses;
    }
}
