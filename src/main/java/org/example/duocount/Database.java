package org.example.duocount;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Database {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);
    private static final String URL = "jdbc:mysql://localhost:3306/duocount";
    private static final String USER = "root";
    private static final String PASSWORD = "zaq1@WSX";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            logger.info("MySQL Driver Loaded Successfully.");
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
            logger.error(e.getMessage());
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
            logger.error(e.getMessage());
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
                logger.info("Calculated share per participant: {}", share);

                for (int participantId : participantIds) {
                    addExpenseParticipant(expenseId, participantId);
                    updateUserBalance(walletId, participantId, -share);
                }

                logger.info("Adding expense: Wallet ID = {}, Payer ID = {}, Description = {}, Amount = {}, Participants = {}", walletId, payerId, description, amount, participantIds);

                return expenseId;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
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
            logger.info("Added participant ID {} to expense ID {}", participantId, expenseId);
        } catch (SQLException e) {
            logger.error(e.getMessage());
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
            logger.info("Updating balance: Wallet ID = {}, User ID = {}, Change = {}", walletId, userId, balanceChange);
        } catch (SQLException e) {
            logger.error(e.getMessage());
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
            logger.error(e.getMessage());
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
            logger.error(e.getMessage());
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
            logger.error(e.getMessage());
        }
        return -1;
    }

    public static List<JsonObject> getWalletDetails(int walletId) {
        String query = """
        SELECT e.id, e.description, e.amount, u.name AS payer
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
                expense.addProperty("id", rs.getInt("id"));
                expense.addProperty("description", rs.getString("description"));
                expense.addProperty("amount", rs.getDouble("amount"));
                expense.addProperty("payer", rs.getString("payer"));
                details.add(expense);
            }
            logger.info("Fetching expense details for wallet ID: {}", walletId);
        } catch (SQLException e) {
            logger.error(e.getMessage());
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
            logger.error(e.getMessage());
        }

        return balances;
    }

    public static boolean deleteExpense(int expenseId, int walletId) {
        String getExpenseDetailsQuery = "SELECT payer_id, amount, number_of_participants FROM expenses WHERE id = ?";
        String getParticipantsQuery = "SELECT participant_id FROM expense_participants WHERE expense_id = ?";
        String deleteExpenseQuery = "DELETE FROM expenses WHERE id = ?";
        String deleteParticipantsQuery = "DELETE FROM expense_participants WHERE expense_id = ?";

        try (Connection conn = getConnection()) {
            logger.info("Deleting Expense ID: {} in Wallet ID: {}", expenseId, walletId);

            int payerId;
            double amount;
            int numberOfParticipants;

            // fetch expense details
            try (PreparedStatement stmt = conn.prepareStatement(getExpenseDetailsQuery)) {
                stmt.setInt(1, expenseId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    logger.warn("Expense ID not found: {}", expenseId);
                    return false;
                }
                payerId = rs.getInt("payer_id");
                amount = rs.getDouble("amount");
                numberOfParticipants = rs.getInt("number_of_participants");
                logger.info("Expense Details - Payer ID: {}, Amount: {}, Participants: {}", payerId, amount, numberOfParticipants);
            }

            // fetch participant IDs
            List<Integer> participantIds = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(getParticipantsQuery)) {
                stmt.setInt(1, expenseId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    int participantId = rs.getInt("participant_id");
                    participantIds.add(participantId);
                    logger.info("Participant ID: {}", participantId);
                }
            }

            // update balances
            double share = amount / numberOfParticipants;
            for (int participantId : participantIds) {
                logger.info("Updating Participant ID: {} with Share: {}", participantId, share);
                updateUserBalance(walletId, participantId, share);
            }
            logger.info("Updating Payer ID: {} with Amount: -{}", payerId, amount);
            updateUserBalance(walletId, payerId, -amount);

            // delete participants
            try (PreparedStatement stmt = conn.prepareStatement(deleteParticipantsQuery)) {
                stmt.setInt(1, expenseId);
                int rowsDeleted = stmt.executeUpdate();
                logger.info("Deleted Participants for Expense ID: {}, Rows Deleted: {}", expenseId, rowsDeleted);
            }

            // delete expense
            try (PreparedStatement stmt = conn.prepareStatement(deleteExpenseQuery)) {
                stmt.setInt(1, expenseId);
                int rowsDeleted = stmt.executeUpdate();
                logger.info("Deleted Expense ID: {}, Rows Deleted: {}", expenseId, rowsDeleted);
            }

            return true;
        } catch (SQLException e) {
            logger.error(e.getMessage());
        }
        return false;
    }


}
