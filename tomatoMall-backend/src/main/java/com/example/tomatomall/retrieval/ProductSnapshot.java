package com.example.tomatomall.retrieval;
import java.math.BigDecimal;
import java.util.List;
/** Detached facts: no JPA entities or lazy relationships cross the retrieval boundary. */
public record ProductSnapshot(int id, String title, BigDecimal price, String tag,
        String description, String detail, String status, int availableStock, List<String> specifications) {
    @com.fasterxml.jackson.annotation.JsonProperty("level")
    public String level() {
        var values=specifications.stream().map(s->s.replace(':','：').trim())
            .filter(s->s.startsWith("适用难度：")).map(s->s.substring(5).trim()).distinct().toList();
        return values.size()==1 && java.util.Set.of("入门","进阶","高级").contains(values.get(0)) ? values.get(0) : null;
    }
}
