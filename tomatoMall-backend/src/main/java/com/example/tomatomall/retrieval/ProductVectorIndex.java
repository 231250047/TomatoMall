package com.example.tomatomall.retrieval;
import java.util.*;
public interface ProductVectorIndex {
    record Match(int productId, double similarity) {}
    List<Match> search(String query,int limit);
    /** Restrict candidates before topK; an empty eligible set returns no results. */
    List<Match> search(String query,int limit,Set<Integer> eligibleIds);
    void sync(int productId);
    Set<Integer> indexedIds();
    Map<String,Object> status();
}
