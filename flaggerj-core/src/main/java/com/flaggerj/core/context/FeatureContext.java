package com.flaggerj.core.context;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An immutable, thread-safe evaluation context passed to {@code FlaggerClient} when resolving a
 * feature flag. Carries the well-known attributes used most commonly for targeting
 * ({@code userId}, {@code tenantId}, {@code country}) plus an arbitrary set of custom
 * string attributes.
 *
 * <p>Instances are built through {@link #builder()} and are safe to share and read concurrently
 * across threads once built, since the backing attribute map is copied into a
 * {@link ConcurrentHashMap} and never mutated afterwards.
 */
public final class FeatureContext {

    private static final FeatureContext EMPTY = new FeatureContext(new Builder());

    private final String userId;
    private final String tenantId;
    private final String country;
    private final Map<String, String> attributes;

    private FeatureContext(Builder builder) {
        this.userId = builder.userId;
        this.tenantId = builder.tenantId;
        this.country = builder.country;
        this.attributes = new ConcurrentHashMap<>(builder.attributes);
    }

    /**
     * Returns a shared, empty evaluation context with no attributes set. Used by generated
     * container implementations when a flag method takes no {@code FeatureContext} argument.
     */
    public static FeatureContext empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUserId() {
        return userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getCountry() {
        return country;
    }

    /**
     * Resolves the value of an attribute by name. The well-known attributes {@code userId},
     * {@code tenantId}, and {@code country} are resolved from their dedicated fields; any other
     * name is looked up in the custom attribute map. Returns {@code null} if the attribute was
     * never set.
     */
    public String getAttribute(String key) {
        if (key == null) {
            return null;
        }
        switch (key) {
            case "userId":
                return userId;
            case "tenantId":
                return tenantId;
            case "country":
                return country;
            default:
                return attributes.get(key);
        }
    }

    /**
     * Returns an unmodifiable view of the custom attributes set on this context. Does not include
     * {@code userId}, {@code tenantId}, or {@code country}; use {@link #getAttribute(String)} to
     * read those uniformly alongside custom attributes.
     */
    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FeatureContext)) {
            return false;
        }
        FeatureContext other = (FeatureContext) o;
        return Objects.equals(userId, other.userId)
                && Objects.equals(tenantId, other.tenantId)
                && Objects.equals(country, other.country)
                && Objects.equals(attributes, other.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, tenantId, country, attributes);
    }

    @Override
    public String toString() {
        return "FeatureContext{"
                + "userId='" + userId + '\''
                + ", tenantId='" + tenantId + '\''
                + ", country='" + country + '\''
                + ", attributes=" + attributes
                + '}';
    }

    /**
     * Mutable builder for {@link FeatureContext}. Not thread-safe; build the context on a single
     * thread and share the resulting immutable instance across threads.
     */
    public static final class Builder {
        private String userId;
        private String tenantId;
        private String country;
        private final Map<String, String> attributes = new ConcurrentHashMap<>();

        private Builder() {
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder country(String country) {
            this.country = country;
            return this;
        }

        public Builder attribute(String key, String value) {
            Objects.requireNonNull(key, "key must not be null");
            this.attributes.put(key, value);
            return this;
        }

        public FeatureContext build() {
            return new FeatureContext(this);
        }
    }
}
