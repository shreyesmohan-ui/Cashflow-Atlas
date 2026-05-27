package com.shrey.cashflowatlas.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CategoryRule {
    public String id;
    public String label;
    public double budget;
    public int color;
    public boolean essential;
    public List<String> keywords;

    public CategoryRule(String id, String label, double budget, int color, boolean essential, List<String> keywords) {
        this.id = id;
        this.label = label;
        this.budget = budget;
        this.color = color;
        this.essential = essential;
        this.keywords = new ArrayList<>(keywords);
    }

    public static CategoryRule of(String id, String label, double budget, int color, boolean essential, String... keywords) {
        return new CategoryRule(id, label, budget, color, essential, Arrays.asList(keywords));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("label", label);
        object.put("budget", budget);
        object.put("color", color);
        object.put("essential", essential);
        JSONArray keywordArray = new JSONArray();
        for (String keyword : keywords) {
            keywordArray.put(keyword);
        }
        object.put("keywords", keywordArray);
        return object;
    }

    public static CategoryRule fromJson(JSONObject object) throws JSONException {
        JSONArray keywordArray = object.optJSONArray("keywords");
        List<String> keywords = new ArrayList<>();
        if (keywordArray != null) {
            for (int i = 0; i < keywordArray.length(); i += 1) {
                keywords.add(keywordArray.getString(i));
            }
        }
        return new CategoryRule(
                object.getString("id"),
                object.getString("label"),
                object.optDouble("budget", 0),
                object.optInt("color", 0xff059669),
                object.optBoolean("essential", true),
                keywords
        );
    }
}
