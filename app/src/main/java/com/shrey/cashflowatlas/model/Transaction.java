package com.shrey.cashflowatlas.model;

import org.json.JSONException;
import org.json.JSONObject;

public class Transaction {
    public String id;
    public String date;
    public String type;
    public String name;
    public String category;
    public double amount;
    public String source;
    public String raw;
    public int confidence;

    public Transaction(String id, String date, String type, String name, String category, double amount, String source, String raw, int confidence) {
        this.id = id;
        this.date = date;
        this.type = type;
        this.name = name;
        this.category = category;
        this.amount = amount;
        this.source = source;
        this.raw = raw;
        this.confidence = confidence;
    }

    public boolean isCredit() {
        return "credit".equals(type);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("date", date);
        object.put("type", type);
        object.put("name", name);
        object.put("category", category);
        object.put("amount", amount);
        object.put("source", source);
        object.put("raw", raw);
        object.put("confidence", confidence);
        return object;
    }

    public static Transaction fromJson(JSONObject object) {
        return new Transaction(
                object.optString("id"),
                object.optString("date"),
                object.optString("type", "debit"),
                object.optString("name", "Unknown"),
                object.optString("category", "shopping"),
                object.optDouble("amount", 0),
                object.optString("source", "manual"),
                object.optString("raw", ""),
                object.optInt("confidence", 100)
        );
    }
}
