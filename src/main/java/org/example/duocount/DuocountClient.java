package org.example.duocount;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
public class DuocountClient extends Application {
    private final String url = "http://localhost:8080";
    private String userName;
    public static void main(String[] args) {
        launch(args);
    }
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Duocount Client");

        showLoginScene(primaryStage);
        primaryStage.show();
    }
    private void showLoginScene(Stage stage) {
        GridPane loginGrid = new GridPane();
        loginGrid.setPadding(new Insets(10));
        loginGrid.setVgap(10);
        loginGrid.setHgap(10);
        Label initLabel = new Label("Enter your username to start:");
        TextField nameField = new TextField();
        nameField.setPromptText("Username");
        Button startButton = new Button("Start");
        loginGrid.add(initLabel, 0, 0);
        loginGrid.add(nameField, 0, 1);
        loginGrid.add(startButton, 0, 2);
        Scene loginScene = new Scene(loginGrid, 400, 200);
        stage.setScene(loginScene);
        startButton.setOnAction(e -> {
            userName = nameField.getText().trim();
            if (userName.isEmpty()) {
                showAlert("Error", "Username cannot be empty.");
                return;
            }
            if (sendPostRequest("/user", "name=" + userName)) {
                showWalletsView(stage);
            } else {
                showAlert("Error", "Failed to login or create user.");
            }
        });
    }
    private void showWalletsView(Stage stage) {
        VBox walletsLayout = new VBox(10);
        walletsLayout.setPadding(new Insets(10));
        Label walletsLabel = new Label("Your Wallets:");
        ListView<String> walletsList = new ListView<>();
        Button addWalletButton = new Button("Add Wallet");
        TextField walletNameField = new TextField();
        walletNameField.setPromptText("New Wallet Name");
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshWallets(walletsList));
        addWalletButton.setOnAction(e -> {
            String walletName = walletNameField.getText().trim();
            if (!walletName.isEmpty()) {
                if (sendPostRequest("/wallet", "name=" + walletName)) {
                    showAlert("Success", "Wallet added: " + walletName);
                    refreshWallets(walletsList);
                    walletNameField.clear();
                } else {
                    showAlert("Error", "Failed to add wallet.");
                }
            } else {
                showAlert("Error", "Wallet name cannot be empty.");
            }
        });
        walletsList.setOnMouseClicked(e -> {
            String selectedWallet = walletsList.getSelectionModel().getSelectedItem();
            if (selectedWallet != null) {
                showWalletDetailsView(stage, selectedWallet);
            }
        });
        walletsLayout.getChildren().addAll(walletsLabel, walletsList, walletNameField, addWalletButton, refreshButton);
        refreshWallets(walletsList);
        Scene walletsScene = new Scene(walletsLayout, 400, 400);
        stage.setScene(walletsScene);
    }
    private void refreshWallets(ListView<String> walletsList) {
        String response = sendGetRequest("/wallets");
        walletsList.getItems().clear();

        if (response == null || response.isEmpty()) {
            showAlert("Error", "Failed to fetch wallets. Please try again later.");
            return;
        }

        try {
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(response, JsonObject.class);
            JsonArray wallets = jsonObject.getAsJsonArray("wallets");
            for (JsonElement walletElement : wallets) {
                walletsList.getItems().add(walletElement.getAsString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to parse wallets response.");
        }
    }

    private void showAddExpenseWindow(Stage parentStage, String walletName) {
        Stage addExpenseStage = new Stage();
        addExpenseStage.setTitle("Add Expense");

        GridPane expenseGrid = new GridPane();
        expenseGrid.setPadding(new Insets(10));
        expenseGrid.setVgap(10);
        expenseGrid.setHgap(10);

        Label descriptionLabel = new Label("Description:");
        TextField descriptionField = new TextField();

        Label amountLabel = new Label("Amount:");
        TextField amountField = new TextField();

        Label participantLabel = new Label("Participants (comma-separated):");
        TextField participantField = new TextField();

        Button addExpenseButton = new Button("Add Expense");
        Button cancelButton = new Button("Cancel");

        expenseGrid.add(descriptionLabel, 0, 0);
        expenseGrid.add(descriptionField, 1, 0);
        expenseGrid.add(amountLabel, 0, 1);
        expenseGrid.add(amountField, 1, 1);
        expenseGrid.add(participantLabel, 0, 2);
        expenseGrid.add(participantField, 1, 2);
        expenseGrid.add(addExpenseButton, 0, 3);
        expenseGrid.add(cancelButton, 1, 3);

        Scene expenseScene = new Scene(expenseGrid, 400, 300);
        addExpenseStage.setScene(expenseScene);

        addExpenseButton.setOnAction(e -> {
            String description = descriptionField.getText().trim();
            String amount = amountField.getText().trim();
            String participants = participantField.getText().trim();

            if (!description.isEmpty() && !amount.isEmpty() && !participants.isEmpty()) {
                String[] participantNames = participants.split(",");
                StringBuilder data = new StringBuilder();
                data.append("wallet=").append(walletName)
                        .append("&description=").append(URLEncoder.encode(description, StandardCharsets.UTF_8))
                        .append("&amount=").append(amount)
                        .append("&payer=").append(userName);
                for (String participant : participantNames) {
                    data.append("&participant=").append(URLEncoder.encode(participant.trim(), StandardCharsets.UTF_8));
                }

                System.out.println("Sending add expense request: " + data);

                if (sendPostRequest("/expense", data.toString())) {
                    showAlert("Success", "Expense added successfully!");
                    addExpenseStage.close();
                    showWalletDetailsView(parentStage, walletName);
                } else {
                    showAlert("Error", "Failed to add expense.");
                }
            } else {
                showAlert("Error", "All fields must be filled.");
            }
        });

        cancelButton.setOnAction(e -> addExpenseStage.close());

        addExpenseStage.initOwner(parentStage);
        addExpenseStage.show();
    }



    private void showWalletDetailsView(Stage stage, String walletName) {
        VBox detailsLayout = new VBox(10);
        detailsLayout.setPadding(new Insets(10));
        Label walletLabel = new Label("Wallet: " + walletName);

        Label expensesLabel = new Label("Expenses:");
        ListView<String> expensesList = new ListView<>();

        Label settlementsLabel = new Label("Settlements:");
        ListView<String> settlementsList = new ListView<>();

        String response = sendGetRequest("/wallet/" + walletName);
        System.out.println("Server response for wallet details: " + response);

        if (response != null && !response.isEmpty()) {
            try {
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(response, JsonObject.class);

                JsonArray expenses = jsonObject.getAsJsonArray("expenses");
                if (expenses != null) {
                    for (JsonElement expenseElement : expenses) {
                        JsonObject expense = expenseElement.getAsJsonObject();
                        String description = expense.get("description").getAsString();
                        double amount = expense.get("amount").getAsDouble();
                        String payer = expense.get("payer").getAsString();
                        expensesList.getItems().add(String.format("%s paid %.2f for %s", payer, amount, description));
                    }
                } else {
                    expensesList.getItems().add("No expenses available.");
                }

                JsonArray settlements = jsonObject.getAsJsonArray("settlements");
                if (settlements != null) {
                    for (JsonElement settlementElement : settlements) {
                        JsonObject settlement = settlementElement.getAsJsonObject();
                        String from = settlement.get("from").getAsString();
                        String to = settlement.get("to").getAsString();
                        double amount = settlement.get("amount").getAsDouble();
                        settlementsList.getItems().add(String.format("%s owes %s %.2f", from, to, amount));
                    }
                } else {
                    settlementsList.getItems().add("No settlements available.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Error", "Failed to load wallet details.");
            }
        } else {
            expensesList.getItems().add("No expenses available.");
            settlementsList.getItems().add("No settlements available.");
        }

        Button addExpenseButton = new Button("Add Expense");
        addExpenseButton.setOnAction(e -> showAddExpenseWindow(stage, walletName));

        Button backButton = new Button("Back");
        backButton.setOnAction(e -> showWalletsView(stage));

        detailsLayout.getChildren().addAll(
                walletLabel,
                expensesLabel,
                expensesList,
                settlementsLabel,
                settlementsList,
                addExpenseButton,
                backButton
        );
        Scene detailsScene = new Scene(detailsLayout, 600, 600);
        stage.setScene(detailsScene);
    }


    private boolean sendPostRequest(String endpoint, String data) {
        try {
            URL url = new URL(this.url + endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = connection.getOutputStream()) {
                os.write(data.getBytes());
            }
            return connection.getResponseCode() == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    private String sendGetRequest(String endpoint) {
        try {
            URL url = new URL(this.url + endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            if (connection.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                return response.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
