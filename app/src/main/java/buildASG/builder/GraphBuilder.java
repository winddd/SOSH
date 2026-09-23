package buildASG.builder;

import buildASG.GraphBuildContext;
import util.exception.RejectException;

/**
 * Strategy interface for graph building components.
 * Each implementation handles one specific aspect of ASG construction.
 */
public interface GraphBuilder {
    /**
     * @return Human-readable name for this builder (used in error messages)
     */
    String getName();

    /**
     * @param context Build context containing ASG, history, and options
     * @return true if this builder should run for the given context
     */
    boolean isApplicable(GraphBuildContext context);

    /**
     * Build the specific graph component this builder is responsible for.
     *
     * @param context Build context containing all needed data
     * @throws RejectException if the graph building fails
     */
    void build(GraphBuildContext context) throws RejectException;
}
