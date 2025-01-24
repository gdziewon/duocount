package org.example.duocount;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DuocountServer {
    private static final Logger logger = LoggerFactory.getLogger(DuocountServer.class);

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        ExecutorService threadPool = Executors.newFixedThreadPool(10);

        server.createContext("/user", new UserHandler());
        server.createContext("/wallet", new WalletHandler());
        server.createContext("/expense", new ExpenseHandler());
        server.createContext("/wallets", new AllWalletsHandler());
        server.createContext("/wallet/", new WalletDetailsHandler(threadPool));
        server.createContext("/deleteExpense", new DeleteExpenseHandler());
        logger.info("Registering endpoint: /user");
        logger.info("Registering endpoint: /wallet");
        logger.info("Registering endpoint: /expense");
        logger.info("Registering endpoint: /wallets");
        logger.info("Registering endpoint: /wallet/");
        logger.info("Registering endpoint: /deleteExpense");



        server.setExecutor(threadPool);

        logger.info("Server started on port 8080");
        server.start();
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        logger.debug("Sending response with status code: {}, body: {}", statusCode, response);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private static Map<String, List<String>> parseRequestBody(String requestBody) {
        logger.debug("Raw request body: {}", requestBody);
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
        logger.debug("Parsed parameters: {}", params);
        return params;
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
            logger.info("Received request to add user with name: {}", name);
            if (userId != -1) {
                sendResponse(exchange, 200, "User added: " + name);
                logger.info("User added successfully with ID: {}", userId);
            } else {
                sendResponse(exchange, 500, "Failed to add user.");
                logger.error("Failed to add user: {}", name);
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
            logger.info("Received request to create wallet with name: {}", name);
            if (walletId != -1) {
                sendResponse(exchange, 200, "Wallet created: " + name);
                logger.info("Wallet created successfully with ID: {}", walletId);
            } else {
                sendResponse(exchange, 500, "Failed to create wallet.");
                logger.error("Failed to create wallet: {}", name);
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
                    if (participantId == -1)
                        throw new IllegalArgumentException("Participant not found: " + participantName);
                    participantIds.add(participantId);
                    logger.info("Adding participant: {} (ID: {})", participantName, participantId);
                }

                logger.info("Creating expense in wallet: {}", walletName);
                logger.info("Payer: {}, Amount: {}", payerName, amount);
                logger.info("Participants: {}", participantIds);

                if (participantIds.isEmpty()) {
                    throw new IllegalArgumentException("At least one participant is required.");
                }

                int expenseId = Database.addExpense(walletId, payerId, description, amount, participantIds);
                logger.info("Received expense creation request for wallet: {}, payer: {}, amount: {}, description: {}", walletName, payerName, amount, description);
                if (expenseId == -1) {
                    logger.error("Failed to create expense for wallet: {}", walletName);
                    throw new IllegalArgumentException("Expense not created.");
                }
                logger.info("Expense created successfully with ID: {}", expenseId);
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

            logger.info("Fetching wallet details for wallet: {}, ID: {}", walletName, walletId);
            Future<JsonObject> task = threadPool.submit(new SettlementCalculator(walletId));
            logger.info("Settlement calculation completed successfully for wallet ID: {}", walletId);

            try {
                JsonObject jsonResponse = task.get();
                logger.info("Wallet details response: {}", jsonResponse);
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

    static class DeleteExpenseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String requestBody = new String(exchange.getRequestBody().readAllBytes());
            logger.info("Request Body: " + requestBody);

            try {
                Map<String, List<String>> rawParams = parseRequestBody(requestBody);
                HashMap<String, String> params = new HashMap<>();
                rawParams.forEach((key, valueList) -> {
                    if (!valueList.isEmpty()) {
                        params.put(key, valueList.get(0));
                    }
                });

                logger.info("Parsed Parameters: {}", params);

                int expenseId = Integer.parseInt(params.get("expenseId"));
                String walletName = params.get("walletName");

                int walletId = Database.getWalletIdByName(walletName);
                if (walletId == -1) {
                    sendResponse(exchange, 404, "Wallet not found.");
                    return;
                }

                logger.info("Deleting Expense ID: {}, Wallet ID: {}", expenseId, walletId);

                boolean success = Database.deleteExpense(expenseId, walletId);
                if (success) {
                    sendResponse(exchange, 200, "Expense deleted successfully.");
                } else {
                    logger.error("Deletion failed for Expense ID: {}, Wallet ID: {}", expenseId, walletId);
                    sendResponse(exchange, 404, "Expense not found or could not be deleted.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Error: " + e.getMessage());
            }
        }
    }
}