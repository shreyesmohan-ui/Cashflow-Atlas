package com.shrey.cashflowatlas.model;

import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FinanceState {
    public FinanceProfile profile;
    public List<CategoryRule> categories;
    public List<Transaction> transactions;

    public FinanceState(FinanceProfile profile, List<CategoryRule> categories, List<Transaction> transactions) {
        this.profile = profile;
        this.categories = categories;
        this.transactions = transactions;
    }

    public static FinanceState demo() {
        List<CategoryRule> categories = new ArrayList<>();
        categories.add(CategoryRule.of("rent", "Rent", 24000, Color.rgb(37, 99, 235), true, "rent", "society", "maintenance", "nobroker"));
        categories.add(CategoryRule.of("food", "Food", 12500, Color.rgb(22, 163, 74), true, "zomato", "swiggy", "grocery", "restaurant", "food"));
        categories.add(CategoryRule.of("transport", "Transport", 5200, Color.rgb(249, 115, 22), true, "uber", "ola", "metro", "fuel", "petrol", "rapido"));
        categories.add(CategoryRule.of("shopping", "Shopping", 8500, Color.rgb(219, 39, 119), false, "amazon", "flipkart", "myntra", "store"));
        categories.add(CategoryRule.of("tools", "Tools", 3800, Color.rgb(124, 58, 237), false, "github", "course", "domain", "aws", "openai", "software"));
        categories.add(CategoryRule.of("bills", "Bills", 7200, Color.rgb(8, 145, 178), true, "airtel", "jio", "electricity", "bill", "wifi", "recharge"));
        categories.add(CategoryRule.of("fun", "Fun", 9000, Color.rgb(234, 179, 8), false, "netflix", "movie", "bookmyshow", "game", "cafe"));
        categories.add(CategoryRule.of("health", "Health", 4200, Color.rgb(220, 38, 38), true, "pharmacy", "apollo", "hospital", "doctor", "medicine"));

        List<Transaction> transactions = new ArrayList<>();
        transactions.add(new Transaction("salary", "2026-05-01", "credit", "Monthly salary", "income", 92000, "demo", "", 100));
        transactions.add(new Transaction("zomato", "2026-05-02", "debit", "Zomato dinner", "food", 849, "demo", "", 100));
        transactions.add(new Transaction("github", "2026-05-04", "debit", "GitHub tools", "tools", 820, "demo", "", 100));
        transactions.add(new Transaction("metro", "2026-05-06", "debit", "Metro card", "transport", 1200, "demo", "", 100));
        transactions.add(new Transaction("rent", "2026-05-08", "debit", "Apartment rent", "rent", 24000, "demo", "", 100));

        return new FinanceState(
                new FinanceProfile(92000, 250000, 400000, 30000),
                categories,
                transactions
        );
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("profile", profile.toJson());
        JSONArray categoryArray = new JSONArray();
        for (CategoryRule category : categories) {
            categoryArray.put(category.toJson());
        }
        object.put("categories", categoryArray);
        JSONArray transactionArray = new JSONArray();
        for (Transaction transaction : transactions) {
            transactionArray.put(transaction.toJson());
        }
        object.put("transactions", transactionArray);
        return object;
    }

    public static FinanceState fromJson(JSONObject object) throws JSONException {
        FinanceProfile profile = FinanceProfile.fromJson(object.getJSONObject("profile"));
        List<CategoryRule> categories = new ArrayList<>();
        JSONArray categoryArray = object.getJSONArray("categories");
        for (int i = 0; i < categoryArray.length(); i += 1) {
            categories.add(CategoryRule.fromJson(categoryArray.getJSONObject(i)));
        }
        List<Transaction> transactions = new ArrayList<>();
        JSONArray transactionArray = object.optJSONArray("transactions");
        if (transactionArray != null) {
            for (int i = 0; i < transactionArray.length(); i += 1) {
                transactions.add(Transaction.fromJson(transactionArray.getJSONObject(i)));
            }
        }
        return new FinanceState(profile, categories, transactions);
    }
}
