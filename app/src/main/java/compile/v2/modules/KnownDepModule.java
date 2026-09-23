package compile.v2.modules;

import compile.v2.inputs.GraphInputs;
import graphs.edges.EdgeType;
import graphs.graphs.InCompleteGraph;
import util.Config;
import util.Profiler;
import util.enumtypes.MODE;

/**
 * Only applies to SSER/SER/SI/RC/RU/RR
 */
public class KnownDepModule implements GenDepModule {
  private Profiler profiler;
  private MODE mode;
  private Config cfg;

  public KnownDepModule(Profiler profiler, MODE mode, Config cfg) {
    this.profiler = profiler;
    this.mode = mode;
    this.cfg = cfg;
  }

  @Override
  public InCompleteGraph generate(GraphInputs inputs, InCompleteGraph g) {
    for (var edge : inputs.asg().getDependencyStore().edges()) { // for all isolation levels
      if (EdgeType.belongToIsolation(edge.edgeType, mode.getIsolationLevel())) {
        g.addEdge(edge);
      }
    }

    return g;
  }
}
