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

    public static void initializeUserBalances(int walletId, List<Integer> userIds) {
        String query = "INSERT INTO user_balances (wallet_id, user_id, balance) VALUES (?, ?, 0) " +
                "ON DUPLICATE KEY UPDATE balance = balance";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            for (int userId : userIds) {
                stmt.setInt(1, walletId);
                stmt.setInt(2, userId);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
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

    public static int addExpense(int walletId, int payerId, String description, double amount, List<Integer> participantIds) {
        String query = "INSERT INTO expenses (wallet_id, payer_id, description, amount, number_of_participants) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, walletId);
            stmt.setInt(2, payerId);
            stmt.setString(3, description);
            stmt.setDouble(4, amount);
            stmt.setInt(5, participantIds.size());
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                int expenseId = keys.getInt(1);

                updateUserBalance(walletId, payerId, amount);

                double share = amount / participantIds.size();
                System.out.println("Calculated share per participant: " + share);

                for (int participantId : participantIds) {
                    addExpenseParticipant(expenseId, participantId);
                    updateUserBalance(walletId, participantId, -share);
                }

                System.out.println("Adding expense: Wallet ID = " + walletId + ", Payer ID = " + payerId +
                        ", Description = " + description + ", Amount = " + amount + ", Participants = " + participantIds);

                return expenseId;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }


    public static void addExpenseParticipant(int expenseId, int participantId) {
        String query = "INSERT INTO expense_participants (expense_id, participant_id) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, expenseId);
            stmt.setInt(2, participantId);
            stmt.executeUpdate();
            System.out.println(String.format("Added participant ID %d to expense ID %d", participantId, expenseId));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void updateUserBalance(int walletId, int userId, double balanceChange) {
        String query = "INSERT INTO user_balances (wallet_id, user_id, balance) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE balance = balance + ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, walletId);
            stmt.setInt(2, userId);
            stmt.setDouble(3, balanceChange);
            stmt.setDouble(4, balanceChange);
            stmt.executeUpdate();
            System.out.println("Updating balance: Wallet ID = " + walletId + ", User ID = " + userId + ", Change = " + balanceChange);
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
        return -1;
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
            System.out.println("Fetching expense details for wallet ID: " + walletId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return details;
    }


    public static HashMap<String, Double> getUserBalances(int walletId) {
        HashMap<String, Double> balances = new HashMap<>();
        String query = """
        SELECT u.name AS user_name, ub.balance
        FROM user_balances ub
        JOIN users u ON ub.user_id = u.id
        WHERE ub.wallet_id = ?
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, walletId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String userName = rs.getString("user_name");
                    double balance = rs.getDouble("balance");
                    balances.put(userName, balance);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return balances;
    }
}
