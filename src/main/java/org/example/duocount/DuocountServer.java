package org.example.duocount;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.example.duocount.Database.getConnection;

public class DuocountServer {

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        ExecutorService threadPool = Executors.newFixedThreadPool(10);

        server.createContext("/user", new UserHandler());
        server.createContext("/wallet", new WalletHandler());
        server.createContext("/expense", new ExpenseHandler());
        server.createContext("/wallets", new AllWalletsHandler());
        server.createContext("/wallet/", new WalletDetailsHandler(threadPool));

        server.setExecutor(threadPool);

        System.out.println("Server started on port 8080");
        server.start();
    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String requestBody = new String(exchange.getRequestBody().readAllBytes());
            String name = requestBody.split("=")[1];

            int userId = Database.addUser(name);
            if (userId != -1) {
                sendResponse(exchange, 200, "User added: " + name);
            } else {
                sendResponse(exchange, 500, "Failed to add user.");
            }
        }
    }

    static class WalletHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String requestBody = new String(exchange.getRequestBody().readAllBytes());
            String name = requestBody.split("=")[1];

            int walletId = Database.addWallet(name);
            if (walletId != -1) {
                sendResponse(exchange, 200, "Wallet created: " + name);
            } else {
                sendResponse(exchange, 500, "Failed to create wallet.");
            }
        }
    }

    static class ExpenseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String requestBody = new String(exchange.getRequestBody().readAllBytes());
            try {
                Map<String, List<String>> params = parseRequestBody(requestBody);
                String payerName = params.get("payer").get(0);
                String walletName = params.get("wallet").get(0);
                String description = params.get("description").get(0);
                double amount = Double.parseDouble(params.get("amount").get(0));

                int walletId = Database.getWalletIdByName(walletName);
                if (walletId == -1) throw new IllegalArgumentException("Wallet not found: " + walletName);

                int payerId = Database.getUserIdByName(payerName);

                List<Integer> participantIds = new ArrayList<>();
                List<String> participantNames = params.getOrDefault("participant", new ArrayList<>());
                for (String participantName : participantNames) {
                    int participantId = Database.getUserIdByName(participantName);
                    if (participantId == -1) throw new IllegalArgumentException("Participant not found: " + participantName);
                    participantIds.add(participantId);
                    System.out.println("Adding participant: " + participantName + " (ID: " + participantId + ")");
                }

                System.out.println("Creating expense in wallet: " + walletName);
                System.out.println("Payer: " + payerName + ", Amount: " + amount);
                System.out.println("Participants: " + participantIds);

                if (participantIds.isEmpty()) {
                    throw new IllegalArgumentException("At least one participant is required.");
                }

                int expenseId = Database.addExpense(walletId, payerId, description, amount, participantIds);
                if (expenseId == -1) {
                    throw new IllegalArgumentException("Expense not created.");
                }
                sendResponse(exchange, 200, "Expense added successfully.");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Error: " + e.getMessage());
            }
        }
    }

    static class WalletDetailsHandler implements HttpHandler {
        private final ExecutorService threadPool;

        public WalletDetailsHandler(ExecutorService threadPool) {
            this.threadPool = threadPool;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String walletName = path.substring(path.lastIndexOf("/") + 1);
            int walletId = Database.getWalletIdByName(walletName);

            if (walletId == -1) {
                sendResponse(exchange, 404, "{\"error\": \"Wallet not found.\"}");
                return;
            }

            Future<JsonObject> task = threadPool.submit(new SettlementCalculator(walletId));

            try {
                JsonObject jsonResponse = task.get();
                System.out.println("Wallet details response: " + jsonResponse);
                sendResponse(exchange, 200, new Gson().toJson(jsonResponse));
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Failed to calculate settlements.\"}");
            }
        }
    }

    static class AllWalletsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            List<String> wallets = Database.getAllWallets();
            JsonObject response = new JsonObject();
            response.addProperty("count", wallets.size());

            JsonArray walletArray = new JsonArray();
            wallets.forEach(walletArray::add);
            response.add("wallets", walletArray);

            sendResponse(exchange, 200, new Gson().toJson(response));
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private static Map<String, List<String>> parseRequestBody(String requestBody) {
        Map<String, List<String>> params = new HashMap<>();
        String[] pairs = requestBody.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0];
                String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);

                params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
        }
        return params;
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
