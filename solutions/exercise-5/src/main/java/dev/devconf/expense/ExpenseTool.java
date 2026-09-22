package dev.devconf.expense;

import dev.langchain4j.agent.tool.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ExpenseTool {

    private static final List<String> CATEGORIES = List.of(
            "Transportation", "Meals", "Accommodation", "Registration", "Supplies");

    private static final Map<String, Double> CATEGORY_LIMITS = Map.of(
            "Meals", 75.0,
            "Transportation", 200.0,
            "Accommodation", 350.0,
            "Registration", 500.0,
            "Supplies", 50.0);

    private final List<Expense> expenseLog = new ArrayList<>();

    @Tool("Log and validate an expense entry against corporate compliance rules. " +
          "Requires: vendor name, amount (numeric), date (YYYY-MM-DD), category " +
          "(Transportation, Meals, Accommodation, Registration, or Supplies), and a brief description.")
    public String logExpense(String vendor, String amount, String date,
                             String category, String description) {
        if (vendor == null || vendor.isBlank()) {
            return "REJECTED: vendor name is required.";
        }
        if (!CATEGORIES.contains(category)) {
            return "REJECTED: invalid category '" + category
                   + "'. Valid categories: " + String.join(", ", CATEGORIES);
        }

        double parsedAmount;
        try {
            parsedAmount = Double.parseDouble(amount.replaceAll("[^\\d.]", ""));
        } catch (NumberFormatException e) {
            return "REJECTED: '" + amount + "' is not a valid amount.";
        }

        Double limit = CATEGORY_LIMITS.get(category);
        boolean compliant = limit == null || parsedAmount <= limit;
        String flag = compliant
                ? ""
                : "OVER LIMIT: $" + String.format("%.2f", parsedAmount)
                  + " exceeds $" + String.format("%.2f", limit)
                  + " cap for " + category;

        String id = "EXP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Expense expense = new Expense(id, vendor, parsedAmount, date, category,
                                      description, compliant, flag);
        expenseLog.add(expense);

        StringBuilder sb = new StringBuilder();
        sb.append("Expense logged successfully.\n");
        sb.append("  ID: ").append(id).append("\n");
        sb.append("  Vendor: ").append(vendor).append("\n");
        sb.append("  Amount: $").append(String.format("%.2f", parsedAmount)).append("\n");
        sb.append("  Date: ").append(date).append("\n");
        sb.append("  Category: ").append(category).append("\n");
        sb.append("  Compliant: ").append(compliant ? "YES" : "NO — " + flag).append("\n");
        return sb.toString();
    }

    @Tool("Process structured receipt data received from other agents (e.g. the Travel Agent). " +
          "Input is a text block with vendor, amount, date, and category fields. " +
          "Validates the data and creates an audit-ready expense entry.")
    public String processReceipt(String receiptData) {
        String vendor = extractField(receiptData, "vendor");
        String amount = extractField(receiptData, "amount");
        String date = extractField(receiptData, "date");
        String category = extractField(receiptData, "category");
        String description = extractField(receiptData, "description");

        if (vendor.isEmpty() || amount.isEmpty()) {
            return "REJECTED: receipt data must include at least vendor and amount.\n"
                   + "Received:\n" + receiptData;
        }

        if (category.isEmpty()) {
            category = "Transportation";
        }
        if (description.isEmpty()) {
            description = "Receipt processed from agent handoff";
        }

        return logExpense(vendor, amount, date, category, description);
    }

    @Tool("Get a summary of all logged expenses, grouped by category with totals.")
    public String getExpenseSummary(String attendeeName) {
        if (expenseLog.isEmpty()) {
            return "No expenses logged yet for " + attendeeName + ".";
        }

        Map<String, List<Expense>> byCategory = expenseLog.stream()
                .collect(Collectors.groupingBy(Expense::category));

        StringBuilder sb = new StringBuilder();
        sb.append("Expense Summary");
        if (attendeeName != null && !attendeeName.isBlank()) {
            sb.append(" for ").append(attendeeName);
        }
        sb.append(":\n\n");

        double grandTotal = 0;
        for (String cat : CATEGORIES) {
            List<Expense> items = byCategory.getOrDefault(cat, List.of());
            if (items.isEmpty()) continue;
            double catTotal = items.stream().mapToDouble(Expense::amount).sum();
            grandTotal += catTotal;
            sb.append("  ").append(cat).append(": $")
              .append(String.format("%.2f", catTotal))
              .append(" (").append(items.size()).append(" entries)\n");
            for (Expense e : items) {
                sb.append("    - ").append(e.vendor())
                  .append(": $").append(String.format("%.2f", e.amount()))
                  .append(" [").append(e.date()).append("]");
                if (!e.compliant()) {
                    sb.append(" ** FLAGGED **");
                }
                sb.append("\n");
            }
        }
        sb.append("\n  GRAND TOTAL: $").append(String.format("%.2f", grandTotal));
        return sb.toString();
    }

    @Tool("Check overall compliance status: lists any flagged or over-limit expenses.")
    public String checkComplianceStatus() {
        if (expenseLog.isEmpty()) {
            return "No expenses logged. Compliance status: N/A";
        }

        List<Expense> flagged = expenseLog.stream()
                .filter(e -> !e.compliant())
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("Compliance Status Report\n");
        sb.append("  Total entries: ").append(expenseLog.size()).append("\n");
        sb.append("  Compliant: ").append(expenseLog.size() - flagged.size()).append("\n");
        sb.append("  Flagged: ").append(flagged.size()).append("\n");

        if (!flagged.isEmpty()) {
            sb.append("\nFlagged Expenses:\n");
            for (Expense e : flagged) {
                sb.append("  - ").append(e.id()).append(": ").append(e.vendor())
                  .append(" $").append(String.format("%.2f", e.amount()))
                  .append(" — ").append(e.flag()).append("\n");
            }
        } else {
            sb.append("\nAll expenses are within policy limits.");
        }
        return sb.toString();
    }

    private String extractField(String data, String fieldName) {
        if (data == null) return "";
        for (String line : data.split("\n")) {
            String lower = line.toLowerCase().trim();
            if (lower.startsWith(fieldName.toLowerCase() + ":")) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
            if (lower.startsWith(fieldName.toLowerCase() + "=")) {
                return line.substring(line.indexOf('=') + 1).trim();
            }
        }
        return "";
    }

    public record Expense(
        String id, String vendor, double amount, String date,
        String category, String description, boolean compliant, String flag
    ) {}
}
