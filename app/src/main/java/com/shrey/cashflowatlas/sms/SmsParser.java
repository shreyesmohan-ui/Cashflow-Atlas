package com.shrey.cashflowatlas.sms;

import com.shrey.cashflowatlas.model.CategoryRule;
import com.shrey.cashflowatlas.model.Transaction;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsParser {
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:rs\\.?|inr|\\u20B9)\\s*([0-9,]+(?:\\.\\d{1,2})?)|([0-9,]+(?:\\.\\d{1,2})?)\\s*(?:rs\\.?|inr)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_DATE_PATTERN = Pattern.compile("(\\d{1,2})[-/ ](jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[-/ ]?(\\d{2,4})?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_DATE_PATTERN = Pattern.compile("(\\d{1,2})[-/](\\d{1,2})[-/](\\d{2,4})");
    private final SmsIntelligence intelligence = new SmsIntelligence();

    public static class Batch {
        public final List<Transaction> transactions = new ArrayList<>();
        public final List<SmsIntelligence.Review> rejected = new ArrayList<>();
        public int total;
    }

    private static class ParseResult {
        public final Transaction transaction;
        public final SmsIntelligence.Review review;

        public ParseResult(Transaction transaction, SmsIntelligence.Review review) {
            this.transaction = transaction;
            this.review = review;
        }
    }

    public List<Transaction> parseMany(List<String> messages, List<CategoryRule> categories) {
        return parseBatch(messages, categories).transactions;
    }

    public Batch parseBatch(List<String> messages, List<CategoryRule> categories) {
        Batch batch = new Batch();
        for (String message : messages) {
            if (message == null || message.trim().isEmpty()) continue;
            batch.total += 1;
            try {
                ParseResult result = parseReviewed(message, categories);
                if (result.transaction != null) {
                    batch.transactions.add(result.transaction);
                } else if (result.review != null) {
                    batch.rejected.add(result.review);
                }
            } catch (Exception exception) {
                batch.rejected.add(intelligence.review(message, null));
            }
        }
        return batch;
    }

    public Transaction parse(String message, List<CategoryRule> categories) {
        return parseReviewed(message, categories).transaction;
    }

    private ParseResult parseReviewed(String message, List<CategoryRule> categories) {
        Transaction candidate = parseCandidate(message, categories);
        SmsIntelligence.Review review = intelligence.review(message, candidate);
        if (candidate == null || review.discard) {
            return new ParseResult(null, review);
        }
        candidate.confidence = Math.min(candidate.confidence, review.score);
        return new ParseResult(candidate, review);
    }

    private Transaction parseCandidate(String message, List<CategoryRule> categories) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(message);
        if (!amountMatcher.find()) {
            return null;
        }

        String amountToken = amountMatcher.group(1) != null ? amountMatcher.group(1) : amountMatcher.group(2);
        double amount = Double.parseDouble(amountToken.replace(",", ""));
        String lower = message.toLowerCase(Locale.ROOT);
        boolean creditWord = lower.matches(".*\\b(credited|received|deposited|refund|cashback|salary)\\b.*");
        boolean debitWord = lower.matches(".*\\b(debited|spent|paid|sent|purchase|withdrawn)\\b.*");
        String type = creditWord && !debitWord ? "credit" : "debit";
        String merchant = extractMerchant(message, type);
        String category = "credit".equals(type) ? "income" : inferCategory(merchant + " " + message, categories);
        int confidence = confidence(message, merchant, category);

        return new Transaction(
                "sms-" + System.currentTimeMillis() + "-" + Math.abs(message.hashCode()),
                extractDate(message),
                type,
                merchant,
                category,
                amount,
                "sms",
                scrubPii(message),
                confidence
        );
    }

    private String scrubPii(String text) {
        return text.replaceAll("(?i)\\b(xx|acct|a/c|account|card|ref)\\s*[0-9xX]{2,12}\\b", "XXXX");
    }

    public String inferCategory(String text, List<CategoryRule> categories) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (CategoryRule category : categories) {
            for (String keyword : category.keywords) {
                if (!keyword.trim().isEmpty() && lower.contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                    return category.id;
                }
            }
        }
        return "shopping";
    }

    private String extractMerchant(String message, String type) {
        Pattern[] patterns = "credit".equals(type)
                ? new Pattern[]{
                    Pattern.compile("(?:by|from|via)\\s+([a-z0-9 .&-]{3,34})(?:\\s+on|\\.|$)", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("credited.*?\\s([a-z][a-z0-9 .&-]{2,28})(?:\\s+on|\\.|$)", Pattern.CASE_INSENSITIVE)
                }
                : new Pattern[]{
                    Pattern.compile("(?:at|to|towards|for)\\s+([a-z0-9 .&-]{3,34})(?:\\s+on|\\s+from|\\s+via|\\.|$)", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("(?:paid|sent).*?\\s+to\\s+([a-z0-9 .&-]{3,34})(?:\\s+on|\\.|$)", Pattern.CASE_INSENSITIVE)
                };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                String group = matcher.group(1);
                if (group != null) {
                    return cleanMerchant(group);
                }
            }
        }
        return "credit".equals(type) ? "Credit received" : "Unknown merchant";
    }

    private String cleanMerchant(String value) {
        String cleaned = value
                .replaceAll("(?i)\\b(a/c|acct|account|card|bank|upi|ref|avl|bal|xx\\d+)\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) {
            return "Unknown merchant";
        }
        String[] words = cleaned.toLowerCase(Locale.ROOT).split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return builder.toString().trim();
    }

    private String extractDate(String message) {
        Calendar calendar = Calendar.getInstance();
        Matcher wordMatcher = WORD_DATE_PATTERN.matcher(message);
        if (wordMatcher.find()) {
            String day = wordMatcher.group(1);
            String month = wordMatcher.group(2);
            if (day != null && month != null) {
                calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(day));
                calendar.set(Calendar.MONTH, monthIndex(month));
                if (wordMatcher.group(3) != null) {
                    calendar.set(Calendar.YEAR, normalizeYear(wordMatcher.group(3)));
                }
                return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
            }
        }

        Matcher numberMatcher = NUMBER_DATE_PATTERN.matcher(message);
        if (numberMatcher.find()) {
            String day = numberMatcher.group(1);
            String month = numberMatcher.group(2);
            String year = numberMatcher.group(3);
            if (day != null && month != null && year != null) {
                calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(day));
                calendar.set(Calendar.MONTH, Integer.parseInt(month) - 1);
                calendar.set(Calendar.YEAR, normalizeYear(year));
            }
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private int normalizeYear(String rawYear) {
        int year = Integer.parseInt(rawYear);
        return year < 100 ? 2000 + year : year;
    }

    private int monthIndex(String month) {
        String key = month.substring(0, 3).toLowerCase(Locale.ROOT);
        String[] months = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (int i = 0; i < months.length; i += 1) {
            if (months[i].equals(key)) return i;
        }
        return Calendar.getInstance().get(Calendar.MONTH);
    }

    private int confidence(String message, String merchant, String category) {
        int score = 52;
        if (!"Unknown merchant".equals(merchant)) score += 18;
        if (!"shopping".equals(category)) score += 16;
        if (message.toLowerCase(Locale.ROOT).matches(".*\\b(upi|card|a/c|bank|credited|debited)\\b.*")) score += 10;
        return Math.min(96, score);
    }
}
