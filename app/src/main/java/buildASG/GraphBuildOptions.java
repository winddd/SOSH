package buildASG;

import lombok.Getter;

/**
 * Immutable value object for graph build options.
 * Replaces multiple boolean parameters with a type-safe, discoverable API.
 */
@Getter
public final class GraphBuildOptions {
    private final boolean sessionOrder;
    private final boolean realtimeOrder;
    private final boolean expectedExecutionOrder;

    public GraphBuildOptions(boolean sessionOrder, boolean realtimeOrder, boolean expectedExecutionOrder) {
        this.sessionOrder = sessionOrder;
        this.realtimeOrder = realtimeOrder;
        this.expectedExecutionOrder = expectedExecutionOrder;
    }

    /**
     * Builder pattern for easy and discoverable construction.
     */
    public static class Builder {
        private boolean sessionOrder = false;
        private boolean realtimeOrder = false;
        private boolean expectedExecutionOrder = false;

        public Builder withSessionOrder() {
            this.sessionOrder = true;
            return this;
        }

        public Builder withRealtimeOrder() {
            this.realtimeOrder = true;
            return this;
        }

        public Builder withExpectedExecutionOrder() {
            this.expectedExecutionOrder = true;
            return this;
        }

        public GraphBuildOptions build() {
            return new GraphBuildOptions(sessionOrder, realtimeOrder, expectedExecutionOrder);
        }
    }

    @Override
    public String toString() {
        return String.format("GraphBuildOptions{sessionOrder=%s, realtimeOrder=%s, expectedExecutionOrder=%s}",
                sessionOrder, realtimeOrder, expectedExecutionOrder);
    }
}