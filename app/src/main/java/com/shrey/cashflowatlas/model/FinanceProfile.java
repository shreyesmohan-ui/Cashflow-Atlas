package com.shrey.cashflowatlas.model;

import org.json.JSONException;
import org.json.JSONObject;

public class FinanceProfile {
    public double monthlyIncome;
    public double emergencyFund;
    public double savingsGoal;
    public double monthlySaveTarget;

    public FinanceProfile(double monthlyIncome, double emergencyFund, double savingsGoal, double monthlySaveTarget) {
        this.monthlyIncome = monthlyIncome;
        this.emergencyFund = emergencyFund;
        this.savingsGoal = savingsGoal;
        this.monthlySaveTarget = monthlySaveTarget;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("monthlyIncome", monthlyIncome);
        object.put("emergencyFund", emergencyFund);
        object.put("savingsGoal", savingsGoal);
        object.put("monthlySaveTarget", monthlySaveTarget);
        return object;
    }

    public static FinanceProfile fromJson(JSONObject object) {
        return new FinanceProfile(
                object.optDouble("monthlyIncome", 92000),
                object.optDouble("emergencyFund", 250000),
                object.optDouble("savingsGoal", 400000),
                object.optDouble("monthlySaveTarget", 30000)
        );
    }
}
