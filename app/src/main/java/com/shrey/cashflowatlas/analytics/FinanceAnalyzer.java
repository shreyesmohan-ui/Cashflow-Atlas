package com.shrey.cashflowatlas.analytics;

import com.shrey.cashflowatlas.model.CategoryRule;
import com.shrey.cashflowatlas.model.FinanceState;
import com.shrey.cashflowatlas.model.Transaction;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FinanceAnalyzer {
    public static class Anomaly {
        public enum Type { DUPLICATE, UNUSUAL_AMOUNT, SECURITY_ALERT }
        public Type type;
        public Transaction transaction;
        public String message;

        public Anomaly(Type type, Transaction transaction, String message) {
            this.type = type;
            this.transaction = transaction;
            this.message = message;
        }
    }

    public static class Summary {
        public double effectiveIncome;
        public double plannedSpend;
        public double actualSpend;
        public double committedSpend;
        public double freeCash;
        public int savingsRate;
        public double discretionarySpend;
        public double runwayMonths;
        public int goalEtaMonths;
        public int optimizedGoalEtaMonths;
        public int importedSmsCount;
        public double importedSmsSpend;
        public double importedSmsCredits;
        public double forecastedMonthlySpend;
        public double dailySafeLimit;
        public double budgetUtilization;
        public double averageConfidence;
        public double remainingBudget;
        public double netCashflow;
        public double dailyBurnRate;
        public double merchantConcentration;
        public double topMerchantSpend;
        public double savingsGoal;
        public int currentDay;
        public int daysLeft;
        public int healthScore;
        public int demoTransactionCount;
        public String healthLabel = "No data";
        public String topMerchant = "None";
        public String mostOverCategory = "None";
        public double mostOverAmount;
        public Transaction largestDebit;
        public Map<String, Double> actualByCategory = new HashMap<>();
        public Map<String, Double> forecastedByCategory = new HashMap<>();
        public Map<String, Double> categoryPressure = new HashMap<>();
        public Map<String, Double> spendByMerchant = new HashMap<>();
        public Map<String, Double> smsSpendByMerchant = new HashMap<>();
        public List<String> insights = new ArrayList<>();
        public List<Anomaly> anomalies = new ArrayList<>();
        public List<String> recurringCandidates = new ArrayList<>();
    }

    public Summary analyze(FinanceState state) {
        Summary summary = new Summary();
        double incomeCredits = 0;
        double otherCredits = 0;
        double confidenceTotal = 0;
        int confidenceCount = 0;
        Map<String, Integer> merchantCounts = new HashMap<>();

        for (CategoryRule category : state.categories) {
            summary.plannedSpend += category.budget;
            summary.actualByCategory.put(category.id, 0.0);
            summary.forecastedByCategory.put(category.id, 0.0);
        }

        Set<String> seenTransactions = new HashSet<>();
        Calendar cal = Calendar.getInstance();
        int currentDay = cal.get(Calendar.DAY_OF_MONTH);
        int totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        summary.currentDay = currentDay;
        summary.daysLeft = Math.max(1, totalDays - currentDay);
        double paceFactor = (double) totalDays / Math.max(1, currentDay);

        for (Transaction transaction : state.transactions) {
            if ("sms".equals(transaction.source)) {
                summary.importedSmsCount += 1;
            }
            if ("demo".equals(transaction.source)) {
                summary.demoTransactionCount += 1;
            }
            confidenceTotal += transaction.confidence;
            confidenceCount += 1;

            if (transaction.isCredit()) {
                if ("sms".equals(transaction.source)) {
                    summary.importedSmsCredits += transaction.amount;
                }
                if ("income".equals(transaction.category)) {
                    incomeCredits += transaction.amount;
                } else {
                    otherCredits += transaction.amount;
                }
            } else {
                String merchant = transaction.name;
                double currentMerchant = summary.spendByMerchant.containsKey(merchant) ? summary.spendByMerchant.get(merchant) : 0;
                summary.spendByMerchant.put(merchant, currentMerchant + transaction.amount);
                int merchantCount = merchantCounts.containsKey(merchant) ? merchantCounts.get(merchant) : 0;
                merchantCounts.put(merchant, merchantCount + 1);
                if ("sms".equals(transaction.source)) {
                    summary.importedSmsSpend += transaction.amount;
                    double currentSmsMerchant = summary.smsSpendByMerchant.containsKey(merchant) ? summary.smsSpendByMerchant.get(merchant) : 0;
                    summary.smsSpendByMerchant.put(merchant, currentSmsMerchant + transaction.amount);
                }
                summary.actualSpend += transaction.amount;
                Double current = summary.actualByCategory.get(transaction.category);
                summary.actualByCategory.put(transaction.category, (current == null ? 0 : current) + transaction.amount);
                if (summary.largestDebit == null || transaction.amount > summary.largestDebit.amount) {
                    summary.largestDebit = transaction;
                }

                CategoryRule rule = findCategory(state, transaction.category);
                if (rule != null && !rule.essential) {
                    summary.discretionarySpend += transaction.amount;
                }
            }

            // Anomaly: Duplicate detection
            String key = transaction.name + "|" + Math.round(transaction.amount) + "|" + transaction.date;
            if (seenTransactions.contains(key)) {
                summary.anomalies.add(new Anomaly(Anomaly.Type.DUPLICATE, transaction, "Potential duplicate: " + transaction.name + " (" + formatRupees(transaction.amount) + ")"));
            }
            seenTransactions.add(key);

            // Anomaly: Security scanner
            if (transaction.raw != null) {
                String raw = transaction.raw.toLowerCase();
                if (raw.contains("unauthorized") || raw.contains("didn't") || raw.contains("blocked") || raw.contains("suspicious")) {
                    summary.anomalies.add(new Anomaly(Anomaly.Type.SECURITY_ALERT, transaction, "Security Alert in SMS: " + transaction.name));
                }
            }
        }

        // Projections
        for (String categoryId : summary.actualByCategory.keySet()) {
            Double actual = summary.actualByCategory.get(categoryId);
            summary.forecastedByCategory.put(categoryId, (actual == null ? 0 : actual) * paceFactor);
        }
        summary.forecastedMonthlySpend = summary.actualSpend * paceFactor;
        summary.budgetUtilization = summary.plannedSpend > 0 ? Math.round((summary.actualSpend / summary.plannedSpend) * 1000.0) / 10.0 : 0;
        summary.averageConfidence = confidenceCount > 0 ? Math.round((confidenceTotal / confidenceCount) * 10.0) / 10.0 : 0;
        summary.recurringCandidates = recurringCandidates(merchantCounts);
        summary.remainingBudget = summary.plannedSpend - summary.actualSpend;
        summary.dailyBurnRate = summary.actualSpend / Math.max(1, currentDay);

        summary.effectiveIncome = Math.max(state.profile.monthlyIncome, incomeCredits) + otherCredits;
        summary.savingsGoal = state.profile.savingsGoal;
        summary.committedSpend = Math.max(summary.plannedSpend, summary.actualSpend);
        summary.freeCash = summary.effectiveIncome - summary.committedSpend;
        summary.netCashflow = summary.effectiveIncome - summary.actualSpend;
        summary.savingsRate = summary.effectiveIncome > 0 ? (int) Math.round((summary.freeCash / summary.effectiveIncome) * 100) : 0;
        
        summary.dailySafeLimit = Math.max(0, summary.freeCash / summary.daysLeft);

        summary.runwayMonths = summary.committedSpend > 0 ? Math.round((state.profile.emergencyFund / summary.committedSpend) * 10.0) / 10.0 : 0;
        summary.goalEtaMonths = summary.freeCash > 0 ? (int) Math.ceil(state.profile.savingsGoal / summary.freeCash) : -1;

        double optimizedFreeCash = summary.freeCash + (summary.discretionarySpend * 0.25);
        summary.optimizedGoalEtaMonths = optimizedFreeCash > 0 ? (int) Math.ceil(state.profile.savingsGoal / optimizedFreeCash) : -1;
        enrichRankedSignals(state, summary);
        scoreHealth(summary);

        summary.insights = buildInsights(state, summary);
        return summary;
    }

    private void enrichRankedSignals(FinanceState state, Summary summary) {
        for (CategoryRule category : state.categories) {
            double actual = getActual(summary, category.id);
            double pressure = category.budget > 0 ? Math.round((actual / category.budget) * 1000.0) / 10.0 : 0;
            summary.categoryPressure.put(category.id, pressure);
            if (category.budget > 0 && actual > category.budget && actual - category.budget > summary.mostOverAmount) {
                summary.mostOverCategory = category.label;
                summary.mostOverAmount = actual - category.budget;
            }
        }
        for (Map.Entry<String, Double> entry : summary.spendByMerchant.entrySet()) {
            if (entry.getValue() > summary.topMerchantSpend) {
                summary.topMerchant = entry.getKey();
                summary.topMerchantSpend = entry.getValue();
            }
        }
        summary.merchantConcentration = summary.actualSpend > 0
                ? Math.round((summary.topMerchantSpend / summary.actualSpend) * 1000.0) / 10.0
                : 0;
    }

    private void scoreHealth(Summary summary) {
        int score = 100;
        if (summary.budgetUtilization > 100) score -= Math.min(30, (int) Math.round(summary.budgetUtilization - 100));
        if (summary.freeCash < 0) score -= 25;
        if (summary.averageConfidence > 0 && summary.averageConfidence < 70) score -= 12;
        if (summary.demoTransactionCount > 0 && summary.importedSmsCount == 0) score -= 18;
        if (!summary.anomalies.isEmpty()) score -= Math.min(18, summary.anomalies.size() * 6);
        if (summary.merchantConcentration > 45) score -= 8;
        if (summary.runwayMonths > 0 && summary.runwayMonths < 3) score -= 10;
        summary.healthScore = Math.max(0, Math.min(100, score));
        if (summary.healthScore >= 82) {
            summary.healthLabel = "Stable";
        } else if (summary.healthScore >= 62) {
            summary.healthLabel = "Needs attention";
        } else {
            summary.healthLabel = "High risk";
        }
    }

    private CategoryRule findCategory(FinanceState state, String id) {
        for (CategoryRule rule : state.categories) {
            if (rule.id.equals(id)) return rule;
        }
        return null;
    }

    private List<String> buildInsights(FinanceState state, Summary summary) {
        List<String> insights = new ArrayList<>();
        double targetGap = summary.freeCash - state.profile.monthlySaveTarget;
        if (targetGap >= 0) {
            insights.add("You are on track for the monthly saving target with a buffer of " + formatRupees(targetGap) + ".");
        } else {
            insights.add("You need to free up " + formatRupees(Math.abs(targetGap)) + " to hit your monthly saving target.");
        }

        CategoryRule mostOver = null;
        double mostOverAmount = 0;
        CategoryRule highest = null;
        double highestSpend = 0;

        for (CategoryRule category : state.categories) {
            double actual = getActual(summary, category.id);
            if (actual > highestSpend) {
                highestSpend = actual;
                highest = category;
            }
            if (category.budget > 0 && actual > category.budget && actual - category.budget > mostOverAmount) {
                mostOver = category;
                mostOverAmount = actual - category.budget;
            }
        }

        if (mostOver != null) {
            insights.add(mostOver.label + " is over budget by " + formatRupees(mostOverAmount) + ".");
        } else {
            insights.add("No category is above its monthly budget yet.");
        }

        if (highest != null && highestSpend > 0) {
            insights.add(highest.label + " is the biggest spend area at " + formatRupees(highestSpend) + ".");
        } else {
            insights.add("Scan SMS alerts or add transactions to reveal spend concentration.");
        }

        if (summary.importedSmsCount > 0) {
            insights.add(summary.importedSmsCount + " imported SMS transactions are powering actual spend, merchant map, risk scan, and category analytics.");
        }

        if (summary.topMerchantSpend > 0) {
            insights.add("Top merchant concentration: " + summary.topMerchant + " is " + summary.merchantConcentration + "% of tracked spend.");
        }

        if (summary.demoTransactionCount > 0 && summary.importedSmsCount == 0) {
            insights.add("Current analytics still include demo transactions. Import SMS or clear demo rows for a real-data view.");
        }

        if (summary.forecastedMonthlySpend > summary.plannedSpend && summary.plannedSpend > 0) {
            insights.add("At this pace, month-end spend projects to " + formatRupees(summary.forecastedMonthlySpend) + ".");
        }

        if (!summary.recurringCandidates.isEmpty()) {
            insights.add("Recurring candidate: " + summary.recurringCandidates.get(0) + ".");
        }

        if (summary.runwayMonths >= 6) {
            insights.add("Emergency runway looks healthy at " + summary.runwayMonths + " months.");
        } else {
            insights.add("Emergency runway is " + summary.runwayMonths + " months; aim for at least 6 months.");
        }

        if (summary.discretionarySpend > 0 && summary.optimizedGoalEtaMonths > 0 && summary.optimizedGoalEtaMonths < summary.goalEtaMonths) {
            int saved = summary.goalEtaMonths - summary.optimizedGoalEtaMonths;
            insights.add("Savings Optimizer: Cutting 25% of " + formatRupees(summary.discretionarySpend) + " discretionary spend saves you " + saved + " months on your goal.");
        }

        return insights;
    }

    private double getActual(Summary summary, String categoryId) {
        Double value = summary.actualByCategory.get(categoryId);
        return value == null ? 0 : value;
    }

    private String formatRupees(double value) {
        return "Rs. " + Math.round(value);
    }

    private List<String> recurringCandidates(Map<String, Integer> merchantCounts) {
        List<String> recurring = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : merchantCounts.entrySet()) {
            if (entry.getValue() >= 2) {
                recurring.add(entry.getKey() + " appears " + entry.getValue() + " times");
            }
        }
        return recurring;
    }
}
