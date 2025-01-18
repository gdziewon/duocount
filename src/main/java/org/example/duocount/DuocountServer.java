package org.example.duocount;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class DuocountServer {
    public static void main(String[] args) throws IOException {
        // Create HTTP server on port 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        ExecutorService threadPool = Executors.newFixedThreadPool(10);

        // Set routes
        server.createContext("/hello", new HelloHandler());
        server.createContext("/expense", new ExpenseHandler(threadPool));
        server.createContext("/user", new UserHandler());
        server.createContext("/wallet", new WalletHandler());

        // Attach thread pool
        server.setExecutor(threadPool);

        // Start server
        System.out.println("Server started on port 8080");
        server.start();
    }

    // Example handler
    static class HelloHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "Hello, World!";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    static class ExpenseHandler implements HttpHandler {
        private final ExecutorService threadPool;

        public ExpenseHandler(ExecutorService threadPool) {
            this.threadPool = threadPool;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());

                Callable<String> task = () -> {
                    // Parsing and database logic here (for simplicity, we'll simulate parsing)
                    String[] parts = requestBody.split("&");
                    int walletId = Integer.parseInt(parts[0].split("=")[1]);
                    int payerId = Integer.parseInt(parts[1].split("=")[1]);
                    String description = parts[2].split("=")[1];
                    double amount = Double.parseDouble(parts[3].split("=")[1]);

                    Database.addExpense(walletId, payerId, description, amount);
                    return "Expense added successfully.";
                };

                try {
                    Future<String> future = threadPool.submit(task);
                    String response = future.get();
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    String response = "Error processing expense.";
                    exchange.sendResponseHeaders(500, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                }
            } else {
                String response = "Invalid Request Method";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String[] parts = requestBody.split("&");
                String name = parts[0].split("=")[1];

                Database.addUser(name);

                String response = "User added: " + name;
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    static class WalletHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String name = requestBody.split("=")[1];

                Database.addWallet(name);

                String response = "Wallet created: " + name;
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }
}