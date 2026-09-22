package com.flaggerj.core.model;

import java.util.List;
import java.util.Objects;

/**
 * A single targeting rule attached to a {@link FlagDefinition}. During evaluation, the value of
 * {@link #getAttribute()} is resolved from the caller's {@code FeatureContext} and compared
 * against {@link #getValues()} using {@link #getOperator()}; the first rule that matches supplies
 * the flag's result via {@link #getResultValue()}.
 */
public final class TargetingRule {

    private final String attribute;
    private final RuleOperator operator;
    private final List<String> values;
    private final String resultValue;

    public TargetingRule(String attribute, RuleOperator operator, List<String> values, String resultValue) {
        this.attribute = Objects.requireNonNull(attribute, "attribute must not be null");
        this.operator = Objects.requireNonNull(operator, "operator must not be null");
        this.values = values == null ? List.of() : List.copyOf(values);
        this.resultValue = Objects.requireNonNull(resultValue, "resultValue must not be null");
    }

    public String getAttribute() {
        return attribute;
    }

    public RuleOperator getOperator() {
        return operator;
    }

    public List<String> getValues() {
        return values;
    }

    public String getResultValue() {
        return resultValue;
    }

    /**
     * Evaluates this rule against an actual attribute value resolved from a
     * {@code FeatureContext}. Always returns {@code false} when {@code actualValue} is
     * {@code null}, since an absent attribute can never satisfy a targeting rule.
     */
    public boolean matches(String actualValue) {
        if (actualValue == null) {
            return false;
        }
        switch (operator) {
            case EQUALS:
                return values.size() == 1 && actualValue.equals(values.get(0));
            case NOT_EQUALS:
                return values.size() == 1 && !actualValue.equals(values.get(0));
            case IN:
                return values.contains(actualValue);
            case NOT_IN:
                return !values.contains(actualValue);
            case CONTAINS:
                return values.stream().anyMatch(actualValue::contains);
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return "TargetingRule{"
                + "attribute='" + attribute + '\''
                + ", operator=" + operator
                + ", values=" + values
                + ", resultValue='" + resultValue + '\''
                + '}';
    }
}
