package com.example.tomatomall.shopping;

import com.example.tomatomall.retrieval.ProductSnapshot;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookPurchaseFilterTest {
    @ParameterizedTest
    @ValueSource(strings = {"50元以下", "Java入门书，50块以下", "50元以内", "不超过50元", "预算50元"})
    void inclusiveUpperBudgetKeepsExactBoundary(String text) {
        var filter = BookPurchaseFilter.fromText(text, true);
        assertThat(filter.accepts(book("49.99", "available", 1))).isTrue();
        assertThat(filter.accepts(book("50", "available", 1))).isTrue();
        assertThat(filter.accepts(book("50.01", "available", 1))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"低于50元", "不到50元"})
    void strictUpperBudgetExcludesExactBoundary(String text) {
        var filter = BookPurchaseFilter.fromText(text, true);
        assertThat(filter.accepts(book("49.99", "available", 1))).isTrue();
        assertThat(filter.accepts(book("50", "available", 1))).isFalse();
        assertThat(filter.accepts(book("50.01", "available", 1))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"不低于50元", "至少50元"})
    void inclusiveLowerBudgetDoesNotAlsoMatchNegatedUpperComparison(String text) {
        var filter = BookPurchaseFilter.fromText(text, true);
        assertThat(filter.accepts(book("49.99", "available", 1))).isFalse();
        assertThat(filter.accepts(book("50", "available", 1))).isTrue();
        assertThat(filter.accepts(book("50.01", "available", 1))).isTrue();
    }

    @Test
    void strictLowerBudgetExcludesExactBoundary() {
        var filter = BookPurchaseFilter.fromText("高于50元", true);
        assertThat(filter.accepts(book("49.99", "available", 1))).isFalse();
        assertThat(filter.accepts(book("50", "available", 1))).isFalse();
        assertThat(filter.accepts(book("50.01", "available", 1))).isTrue();
    }

    @Test
    void multipleConstraintsIntersectRegardlessOfOrder() {
        var filter = BookPurchaseFilter.fromText("不超过80元，至少20元，50元以下，不低于30元", true);
        assertThat(filter.accepts(book("29.99", "available", 1))).isFalse();
        assertThat(filter.accepts(book("30", "available", 1))).isTrue();
        assertThat(filter.accepts(book("50", "available", 1))).isTrue();
        assertThat(filter.accepts(book("50.01", "available", 1))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"不低于60元，50元以下", "高于50元，50元以下", "低于0元"})
    void impossibleBudgetRequiresClarification(String text) {
        assertThatThrownBy(() -> BookPurchaseFilter.fromText(text, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("澄清");
    }

    @Test
    void stockOnlyRequiresBothAvailabilityAndPositiveStock() {
        var filter = BookPurchaseFilter.fromText("50元以下", true);
        assertThat(filter.accepts(book("40", "available", 1))).isTrue();
        assertThat(filter.accepts(book("40", "available", 0))).isFalse();
        assertThat(filter.accepts(book("40", "available", -1))).isFalse();
        assertThat(filter.accepts(book("40", "unavailable", 5))).isFalse();
    }

    @Test
    void detailLookupRetainsUnavailableAndEmptyStockWithinBudget() {
        var filter = BookPurchaseFilter.fromText("50元以下", false);
        assertThat(filter.accepts(book("40", "unavailable", 0))).isTrue();
        assertThat(filter.accepts(book("40", "available", 0))).isTrue();
        assertThat(filter.accepts(book("51", "unavailable", 0))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"50元左右", "大概50元", "最好便宜点", "预算大约50元", "便宜一点"})
    void vagueBudgetRequiresClarification(String text) {
        assertThatThrownBy(() -> BookPurchaseFilter.fromText(text, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("澄清");
    }

    @ParameterizedTest
    @ValueSource(strings = {"两本总共50元", "总预算50元", "合计50元", "一共50元", "总价50元"})
    void combinedBudgetMustBeClarifiedAsPerBook(String text) {
        assertThatThrownBy(() -> BookPurchaseFilter.fromText(text, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("单本预算");
    }

    @ParameterizedTest
    @ValueSource(strings = {"五十元以下", "预算一百块", "不超过五十元"})
    void unsupportedChineseAmountsRequestDigits(String text) {
        assertThatThrownBy(() -> BookPurchaseFilter.fromText(text, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("数字");
    }

    @Test
    void unrelatedYearsAndIsbnAreNotBudgets() {
        var filter = BookPurchaseFilter.fromText("Java 17，2024年出版，ISBN 9787302628888", false);
        assertThat(filter.minPrice()).isNull();
        assertThat(filter.maxPrice()).isNull();
        assertThat(filter.accepts(book("500", "unavailable", 0))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Java入门书，预算50", "Java入门书，预算￥50"})
    void explicitNumericBudgetNeedsNoCurrencySuffix(String text) {
        var filter = BookPurchaseFilter.fromText(text, true);
        assertThat(filter.accepts(book("50", "available", 1))).isTrue();
        assertThat(filter.accepts(book("50.01", "available", 1))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"预算五十", "预算￥五十", "预算￥很多"})
    void unsupportedExplicitBudgetRequiresClarification(String text) {
        assertThatThrownBy(() -> BookPurchaseFilter.fromText(text, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("澄清");
    }

    @ParameterizedTest
    @ValueSource(strings = {"一共有哪些余华的书", "《便宜的艺术》多少钱", "《50元左右》多少钱"})
    void titleAndOrdinaryCountQuestionDoNotBecomeBudgets(String text) {
        var filter = BookPurchaseFilter.fromText(text, false);
        assertThat(filter.minPrice()).isNull();
        assertThat(filter.maxPrice()).isNull();
    }

    @Test
    void approximatePageCountDoesNotInvalidateExactPriceBudget() {
        var filter = BookPurchaseFilter.fromText("大约20页的书，50元以下", true);
        assertThat(filter.accepts(book("50", "available", 1))).isTrue();
        assertThat(filter.accepts(book("50.01", "available", 1))).isFalse();
    }

    @Test
    void unitlessInclusiveComparisonStillLimitsPrice() {
        var filter = BookPurchaseFilter.fromText("Java书，不超过50", true);
        assertThat(filter.accepts(book("50", "available", 1))).isTrue();
        assertThat(filter.accepts(book("50.01", "available", 1))).isFalse();
    }

    @Test
    void unitlessStrictComparisonStillLimitsPrice() {
        var filter = BookPurchaseFilter.fromText("低于50", true);
        assertThat(filter.accepts(book("49.99", "available", 1))).isTrue();
        assertThat(filter.accepts(book("50", "available", 1))).isFalse();
    }

    @Test
    void pageCountComparisonIsNotAPriceConstraint() {
        var filter = BookPurchaseFilter.fromText("不超过50页", false);
        assertThat(filter.minPrice()).isNull();
        assertThat(filter.maxPrice()).isNull();
    }

    @Test
    void unitlessChineseComparisonRequiresDigits() {
        assertThatThrownBy(() -> BookPurchaseFilter.fromText("不超过五十", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("数字");
    }

    @Test
    void budgetManagementTopicIsNotAPriceRequest() {
        var filter = BookPurchaseFilter.fromText("推荐预算管理入门书", true);
        assertThat(filter.minPrice()).isNull();
        assertThat(filter.maxPrice()).isNull();
    }

    @Test
    void countQuestionWithPerBookLimitDoesNotBecomeCombinedBudget() {
        var filter = BookPurchaseFilter.fromText("一共有哪些50元以下的余华的书", true);
        assertThat(filter.accepts(book("50", "available", 1))).isTrue();
        assertThat(filter.accepts(book("50.01", "available", 1))).isFalse();
    }

    @Test
    void constructorRejectsNegativeOverPreciseAndReversedBounds() {
        assertThatThrownBy(() -> new BookPurchaseFilter(new BigDecimal("-0.01"), null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookPurchaseFilter(null, new BigDecimal("-0.01"), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookPurchaseFilter(new BigDecimal("0.001"), null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookPurchaseFilter(null, new BigDecimal("50.001"), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookPurchaseFilter(new BigDecimal("60"), new BigDecimal("50"), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ProductSnapshot book(String price, String status, int stock) {
        return new ProductSnapshot(1, "Java入门", new BigDecimal(price), "science", "", "", status, stock, List.of());
    }
}
