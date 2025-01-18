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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

public class DuocountServer {

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        ExecutorService threadPool = Executors.newFixedThreadPool(10);

        server.createContext("/hello", new HelloHandler());
        server.createContext("/expense", new ExpenseHandler());
        server.createContext("/user", new UserHandler());
        server.createContext("/wallet", new WalletHandler());
        server.createContext("/wallets", new AllWalletsHandler());
        server.createContext("/wallet/", new WalletDetailsHandler(threadPool));

        server.setExecutor(threadPool);

        System.out.println("Server started on port 8080");
        server.start();
    }

    static class HelloHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendResponse(exchange, 200, "Hello, World!");
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
                HashMap<String, String> params = parseRequestBody(requestBody);
                String walletName = params.get("wallet");
                String description = params.get("description");
                double amount = Double.parseDouble(params.get("amount"));
                String payerName = params.get("payer");

                int walletId = Database.getWalletIdByName(walletName);
                if (walletId == -1) throw new IllegalArgumentException("Wallet not found: " + walletName);

                int payerId = Database.getUserIdByName(payerName);
                if (payerId == -1) throw new IllegalArgumentException("Payer not found: " + payerName);

                int expenseId = Database.addExpense(walletId, payerId, description, amount);

                List<Integer> participantIds = new ArrayList<>();
                for (String key : params.keySet()) {
                    if (key.startsWith("participant")) {
                        String participantName = params.get(key);
                        int participantId = Database.getUserIdByName(participantName);
                        if (participantId == -1) throw new IllegalArgumentException("Participant not found: " + participantName);
                        participantIds.add(participantId);
                    }
                }

                double share = amount / participantIds.size();

                for (int participantId : participantIds) {
                    Database.addExpenseParticipant(expenseId, participantId, share);
                }

                // Payer covers their own full payment
                if (!participantIds.contains(payerId)) {
                    Database.addExpenseParticipant(expenseId, payerId, -amount);
                }

                sendResponse(exchange, 200, "Expense added successfully.");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Error: " + e.getMessage());
            }
        }
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
                sendResponse(exchange, 200, "User logged in: " + name + " (ID: " + userId + ")");
            } else {
                sendResponse(exchange, 500, "Failed to add or log in user.");
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

    private static HashMap<String, String> parseRequestBody(String requestBody) {
        HashMap<String, String> params = new HashMap<>();
        String[] pairs = requestBody.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        }
        return params;
    }

}
