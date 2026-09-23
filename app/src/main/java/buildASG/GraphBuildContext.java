package buildASG;

import java.util.Objects;

import asg.ASG;
import history.KVHistory;
import lombok.Getter;

/**
 * Immutable context object containing all data needed for graph building, serving as the input to ASG builders.
 */
@Getter
public final class GraphBuildContext {
    private final ASG asg;
    private final KVHistory history;
    private final GraphBuildOptions options;

    public GraphBuildContext(ASG asg, KVHistory history, GraphBuildOptions options) {
        this.asg = Objects.requireNonNull(asg, "asg");
        this.history = Objects.requireNonNull(history, "history");
        this.options = Objects.requireNonNull(options, "options");
    }
}
