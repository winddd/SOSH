package compile.v2;

import compile.v2.compilers.GraphCompiler;
import compile.v2.inputs.GraphInputs;
import graphs.graphs.InCompleteGraph;

import java.util.List;

/**
 * Adapter that lets existing v1 graph compilers satisfy the v2 interface while
 * the modular pipeline is migrated incrementally.
 */
public class LegacyGraphCompilerAdapter implements GraphCompiler {
  private final compile.v1.GraphCompiler delegate;

  public LegacyGraphCompilerAdapter(compile.v1.GraphCompiler delegate) {
    this.delegate = delegate;
  }

  @Override
  public List<InCompleteGraph> compile(GraphInputs inputs) {
    return List.of(delegate.compile(inputs.asg()));
  }
}
