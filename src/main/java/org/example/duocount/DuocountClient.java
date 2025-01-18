package org.example.duocount;

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

public class DuocountClient extends Application {

    private String userName;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Duocount Client");

        // User Initialization Scene
        GridPane userGrid = new GridPane();
        userGrid.setPadding(new Insets(10, 10, 10, 10));
        userGrid.setVgap(10);
        userGrid.setHgap(10);

        Label initLabel = new Label("Enter your username to start:");
        TextField nameField = new TextField();
        nameField.setPromptText("Username");
        Button startButton = new Button("Start");

        userGrid.add(initLabel, 0, 0);
        userGrid.add(nameField, 0, 1);
        userGrid.add(startButton, 0, 2);

        Scene initScene = new Scene(userGrid, 400, 200);
        primaryStage.setScene(initScene);

        startButton.setOnAction(e -> {
            userName = nameField.getText();
            if (userName.isEmpty()) {
                showAlert("Error", "Username cannot be empty.");
                return;
            }

            boolean success = sendPostRequest("/user", "name=" + userName);
            if (success) {
                showMainInterface(primaryStage);
            } else {
                showAlert("Error", "Failed to initialize user.");
            }
        });

        primaryStage.show();
    }

    private void showMainInterface(Stage stage) {
        // Main Interface
        VBox mainLayout = new VBox(10);
        mainLayout.setPadding(new Insets(10, 10, 10, 10));

        // Add Wallet Section
        Label walletLabel = new Label("Add Wallet:");
        TextField walletNameField = new TextField();
        walletNameField.setPromptText("Wallet Name");
        Button addWalletButton = new Button("Add Wallet");

        addWalletButton.setOnAction(e -> {
            String walletName = walletNameField.getText();
            if (!walletName.isEmpty()) {
                boolean success = sendPostRequest("/wallet", "name=" + walletName);
                if (success) {
                    showAlert("Success", "Wallet added: " + walletName);
                } else {
                    showAlert("Error", "Failed to add wallet.");
                }
            } else {
                showAlert("Error", "Wallet name cannot be empty.");
            }
        });

        // View Wallets Section
        Button viewWalletsButton = new Button("View Wallets");
        viewWalletsButton.setOnAction(e -> showWallets(stage));

        mainLayout.getChildren().addAll(walletLabel, walletNameField, addWalletButton, viewWalletsButton);

        Scene mainScene = new Scene(mainLayout, 400, 300);
        stage.setScene(mainScene);
    }

    private void showWallets(Stage stage) {
        VBox walletLayout = new VBox(10);
        walletLayout.setPadding(new Insets(10, 10, 10, 10));

        Label walletIdLabel = new Label("Enter Wallet ID to View Details:");
        TextField walletIdField = new TextField();
        walletIdField.setPromptText("Wallet ID");
        Button viewWalletButton = new Button("View Wallet");
        TextArea walletDetails = new TextArea();
        walletDetails.setEditable(false);

        viewWalletButton.setOnAction(e -> {
            String walletId = walletIdField.getText();
            if (!walletId.isEmpty()) {
                String response = sendGetRequest("/wallet/" + walletId);
                walletDetails.setText(response != null ? response : "Failed to fetch wallet details.");
            } else {
                showAlert("Error", "Wallet ID cannot be empty.");
            }
        });

        walletLayout.getChildren().addAll(walletIdLabel, walletIdField, viewWalletButton, walletDetails);

        Scene walletScene = new Scene(walletLayout, 400, 400);
        stage.setScene(walletScene);
    }

    private boolean sendPostRequest(String endpoint, String data) {
        try {
            URL url = new URL("http://localhost:8080" + endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            try (OutputStream os = connection.getOutputStream()) {
                os.write(data.getBytes());
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String sendGetRequest(String endpoint) {
        try {
            URL url = new URL("http://localhost:8080" + endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
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

    public static void main(String[] args) {
        launch(args);
    }
}
