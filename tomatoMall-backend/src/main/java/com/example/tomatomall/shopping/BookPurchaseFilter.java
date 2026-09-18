package com.example.tomatomall.shopping;

import com.example.tomatomall.retrieval.ProductSnapshot;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Deterministic purchase constraints derived only from the user's original text. */
public record BookPurchaseFilter(BigDecimal minPrice, BigDecimal maxPrice, boolean inStockOnly) {
    private static final BigDecimal CENT = new BigDecimal("0.01");
    private static final String AMOUNT = "(?<![\\d.])[-+]?\\d+(?:\\.\\d+)?";
    private static final Pattern CONDITION = Pattern.compile(
            "(?<![不没无])(?:(不超过|不高于|不低于|至少|低于|不到|高于|预算)\\s*(" + AMOUNT
                    + ")\\s*[元块]|(" + AMOUNT + ")\\s*[元块]\\s*(以下|以内|以上))");
    private static final Pattern MONEY = Pattern.compile(AMOUNT + "\\s*[元块]");
    private static final Pattern CHINESE_MONEY = Pattern.compile("[零〇一二两三四五六七八九十百千万亿]+\\s*[元块]");
    private static final Pattern BOOK_TITLE = Pattern.compile("《[^》]*》");
    private static final String COMPARISON = "不超过|不高于|不低于|至少|低于|不到|高于|预算";
    private static final String CLAUSE_END = "(?=\\s*(?:$|[，,。；;！？!?、]))";
    private static final Pattern BUDGET_WITHOUT_UNIT = Pattern.compile(
            "(?<![不没无])(" + COMPARISON + ")\\s*(" + AMOUNT + ")(?![\\d.])" + CLAUSE_END);
    private static final Pattern CHINESE_BUDGET_WITHOUT_UNIT = Pattern.compile(
            "(?:" + COMPARISON + ")\\s*[零〇一二两三四五六七八九十百千万亿]+" + CLAUSE_END);
    private static final Pattern TOTAL_BUDGET = Pattern.compile(
            "(?:总共|总预算|合计|一共|总价)\\s*(?:(?:" + COMPARISON + ")\\s*)?[￥¥]?\\s*(?:"
                    + AMOUNT + "|[零〇一二两三四五六七八九十百千万亿]+)");
    private static final Pattern UNRECOGNIZED_BUDGET = Pattern.compile(
            "预算\\s*(?:[￥¥]|[-+]?\\d|[零〇一二两三四五六七八九十百千万亿]+)");
    private static final Pattern VAGUE_BUDGET = Pattern.compile(
            "(?:大概|大约|差不多)\\s*(?:预算\\s*)?[￥¥]?\\s*" + AMOUNT + "\\s*[元块]"
                    + "|" + AMOUNT + "\\s*[元块]\\s*(?:左右|上下)");

    public BookPurchaseFilter {
        validateAmount(minPrice);
        validateAmount(maxPrice);
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("请澄清单本预算：最低价不能超过最高价");
        }
    }

    public static BookPurchaseFilter fromText(String original, boolean inStockOnly) {
        String text = BOOK_TITLE.matcher(original == null ? "" : original).replaceAll(" ");
        text = text.replaceAll("预算\\s*[￥¥]\\s*(?=[-+]?\\d)", "预算");
        text = BUDGET_WITHOUT_UNIT.matcher(text).replaceAll("$1$2元");
        if (TOTAL_BUDGET.matcher(text).find()) {
            throw new IllegalArgumentException("请澄清每本书的单本预算，暂不支持多本合计预算");
        }
        if (CHINESE_MONEY.matcher(text).find() || CHINESE_BUDGET_WITHOUT_UNIT.matcher(text).find()) {
            throw new IllegalArgumentException("请使用阿拉伯数字澄清单本预算，例如：50元以下");
        }
        if (text.contains("便宜") || VAGUE_BUDGET.matcher(text).find()) {
            throw new IllegalArgumentException("请澄清明确的单本预算边界，例如：50元以下");
        }

        BigDecimal minimum = null;
        BigDecimal maximum = null;
        var conditions = CONDITION.matcher(text);
        StringBuilder remaining = new StringBuilder(text);
        while (conditions.find()) {
            String comparison = conditions.group(1) == null ? conditions.group(4) : conditions.group(1);
            BigDecimal amount = new BigDecimal(conditions.group(2) == null ? conditions.group(3) : conditions.group(2));
            validateAmount(amount);
            switch (comparison) {
                case "不低于", "至少", "以上" -> minimum = minimum == null ? amount : minimum.max(amount);
                case "高于" -> {
                    BigDecimal lower = amount.add(CENT);
                    minimum = minimum == null ? lower : minimum.max(lower);
                }
                default -> {
                    BigDecimal upper = comparison.equals("低于") || comparison.equals("不到") ? amount.subtract(CENT) : amount;
                    maximum = maximum == null ? upper : maximum.min(upper);
                }
            }
            // Preserve offsets so an unsupported second amount cannot be silently ignored.
            for (int i = conditions.start(); i < conditions.end(); i++) {
                remaining.setCharAt(i, ' ');
            }
        }
        if (MONEY.matcher(remaining).find() || UNRECOGNIZED_BUDGET.matcher(remaining).find()) {
            throw new IllegalArgumentException("请用阿拉伯数字澄清单本预算的上下限，例如：至少30元且50元以下");
        }
        return new BookPurchaseFilter(minimum, maximum, inStockOnly);
    }

    public boolean accepts(ProductSnapshot product) {
        if (inStockOnly && (!"available".equals(product.status()) || product.availableStock() <= 0)) {
            return false;
        }
        return (minPrice == null || product.price() != null && product.price().compareTo(minPrice) >= 0)
                && (maxPrice == null || product.price() != null && product.price().compareTo(maxPrice) <= 0);
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount != null && (amount.signum() < 0 || amount.scale() > 2)) {
            throw new IllegalArgumentException("请澄清单本预算：金额必须非负且最多保留两位小数");
        }
    }
}
