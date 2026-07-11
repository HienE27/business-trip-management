package com.hospital.scheduler.repository;

/**
 * Tiny key/value DTO used as a JPQL constructor-expression target so we can
 * bulk-load every {@code AlgorithmConfig} row into a {@code Map<String,String>}
 * with a single SELECT — instead of one SELECT per key (the N+1 pattern that
 * was making the algorithm-config page load take 5+ seconds).
 *
 * <p>Hibernate's JPQL {@code SELECT new ...} only supports fully-qualified class
 * names whose constructor matches the projection, hence this dedicated DTO
 * instead of {@code java.util.AbstractMap.SimpleEntry}.
 */
public final class AlgorithmConfigKeyValue {
    private final String paramKey;
    private final String paramValue;

    public AlgorithmConfigKeyValue(String paramKey, String paramValue) {
        this.paramKey = paramKey;
        this.paramValue = paramValue;
    }

    public String getParamKey() {
        return paramKey;
    }

    public String getParamValue() {
        return paramValue;
    }
}
