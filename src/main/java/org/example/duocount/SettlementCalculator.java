package org.example.duocount;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

public class SettlementCalculator implements Callable<JsonObject> {
    private final int walletId;

    public SettlementCalculator(int walletId) {
        this.walletId = walletId;
    }

    @Override
    public JsonObject call() {
        System.out.println("Starting settlement calculation for wallet ID: " + walletId);

        HashMap<String, Double> netBalances = Database.getUserBalances(walletId);
        System.out.println("Net balances retrieved: " + netBalances);

        List<JsonObject> expenses = Database.getWalletDetails(walletId);
        System.out.println("Expenses retrieved: " + expenses);

        List<JsonObject> settlements = calculateSettlements(netBalances);
        System.out.println("Settlements calculated: " + settlements);

        JsonObject jsonResponse = new JsonObject();

        JsonArray expensesArray = new JsonArray();
        expenses.forEach(expensesArray::add);
        jsonResponse.add("expenses", expensesArray);

        JsonArray settlementsArray = new JsonArray();
        settlements.forEach(settlementsArray::add);
        jsonResponse.add("settlements", settlementsArray);

        System.out.println("Response JSON: " + jsonResponse);
        return jsonResponse;
    }

    private List<JsonObject> calculateSettlements(HashMap<String, Double> netBalances) {
        List<JsonObject> settlements = new ArrayList<>();
        List<String> creditors = new ArrayList<>();
        List<String> debtors = new ArrayList<>();
        HashMap<String, Double> credits = new HashMap<>();
        HashMap<String, Double> debits = new HashMap<>();

        // Split balances into creditors and debtors
        for (var entry : netBalances.entrySet()) {
            String participant = entry.getKey();
            double balance = entry.getValue();
            if (balance > 0) {
                credits.put(participant, balance);
                creditors.add(participant);
            } else if (balance < 0) {
                debits.put(participant, -balance);
                debtors.add(participant);
            }
        }

        // Match creditors and debtors to minimize the number of transactions
        int creditorIndex = 0, debtorIndex = 0;
        while (creditorIndex < creditors.size() && debtorIndex < debtors.size()) {
            String creditor = creditors.get(creditorIndex);
            String debtor = debtors.get(debtorIndex);

            double credit = credits.get(creditor);
            double debit = debits.get(debtor);

            double amount = Math.min(credit, debit);

            JsonObject settlement = new JsonObject();
            settlement.addProperty("from", debtor);
            settlement.addProperty("to", creditor);
            settlement.addProperty("amount", amount);
            settlements.add(settlement);

            // Update remaining balances
            credits.put(creditor, credit - amount);
            debits.put(debtor, debit - amount);

            // Move to the next creditor or debtor if balance is zero
            if (credits.get(creditor) == 0) creditorIndex++;
            if (debits.get(debtor) == 0) debtorIndex++;
        }

        return settlements;
    }
}
