package com.shrey.cashflowatlas.sms;

import com.shrey.cashflowatlas.model.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class SmsIntelligence {
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:rs\\.?|inr|\\u20B9)\\s*[0-9,]+(?:\\.\\d{1,2})?|[0-9,]+(?:\\.\\d{1,2})?\\s*(?:rs\\.?|inr)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_PATTERN = Pattern.compile("(https?://|www\\.|bit\\.ly|tinyurl|t\\.me/|wa\\.me/)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTP_PATTERN = Pattern.compile("\\b(otp|one time password|verification code|login code)\\b", Pattern.CASE_INSENSITIVE);

    public static class Review {
        public final String label;
        public final String reason;
        public final String action;
        public final int score;
        public final boolean discard;
        public final boolean financeCandidate;
        public final String rawPreview;

        public Review(String label, String reason, String action, int score, boolean discard, boolean financeCandidate, String rawPreview) {
            this.label = label;
            this.reason = reason;
            this.action = action;
            this.score = score;
            this.discard = discard;
            this.financeCandidate = financeCandidate;
            this.rawPreview = rawPreview;
        }
    }

    public Review review(String message, Transaction candidate) {
        String raw = message == null ? "" : message.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        List<String> reasons = new ArrayList<>();

        boolean hasAmount = AMOUNT_PATTERN.matcher(raw).find();
        boolean hasLink = LINK_PATTERN.matcher(raw).find();
        boolean hasOtp = OTP_PATTERN.matcher(raw).find();
        boolean hasDirection = lower.matches(".*\\b(credited|debited|paid|sent|spent|received|deposited|withdrawn|purchase|refund|salary)\\b.*");
        boolean hasBankSignal = lower.matches(".*\\b(bank|a/c|acct|account|card|upi|imps|neft|rtgs|wallet|available balance|avl bal)\\b.*");
        boolean promoOnly = lower.matches(".*\\b(congratulations|congrats|won|winner|reward|offer|coupon|limited time|claim|free|prize|gift card|loan approved)\\b.*") && !hasDirection;
        boolean phishingTone = lower.matches(".*\\b(urgent|blocked|kyc|verify now|click|login|password|expire|suspended)\\b.*") && hasLink;

        int score = 42;
        if (hasAmount) {
            score += 18;
            reasons.add("amount found");
        } else {
            score -= 32;
            reasons.add("no amount");
        }
        if (hasDirection) {
            score += 18;
            reasons.add("transaction verb");
        }
        if (hasBankSignal) {
            score += 15;
            reasons.add("banking signal");
        }
        if (candidate != null && !"Unknown merchant".equals(candidate.name)) {
            score += 8;
            reasons.add("merchant extracted");
        }
        if (promoOnly) {
            score -= 38;
            reasons.add("promo/reward language");
        }
        if (hasOtp && !hasDirection) {
            score -= 35;
            reasons.add("OTP/login message");
        }
        if (phishingTone) {
            score -= 30;
            reasons.add("link + urgency");
        } else if (hasLink && !hasDirection) {
            score -= 18;
            reasons.add("external link");
        }

        score = Math.max(0, Math.min(99, score));
        boolean financeCandidate = hasAmount && (hasDirection || hasBankSignal) && candidate != null;
        boolean discard = !financeCandidate || promoOnly || (hasOtp && !hasDirection) || phishingTone || score < 58;
        String label = discard ? "Discarded" : score >= 82 ? "Trusted" : "Review";
        String action = discard ? "Not imported" : "Ready to import";
        return new Review(label, joinReasons(reasons), action, score, discard, financeCandidate, preview(raw));
    }

    private String joinReasons(List<String> reasons) {
        if (reasons.isEmpty()) return "No strong finance signal";
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(3, reasons.size());
        for (int i = 0; i < limit; i += 1) {
            if (i > 0) builder.append(", ");
            builder.append(reasons.get(i));
        }
        return builder.toString();
    }

    private String preview(String value) {
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() > 150 ? compact.substring(0, 150) + "..." : compact;
    }
}
