package compile.v2.modules;

import compile.v2.inputs.GraphInputs;
import graphs.constraints.Imply;
import graphs.edges.EdgeType;
import graphs.graphs.BoomslangGraph;
import graphs.graphs.InCompleteGraph;
import util.Config;
import util.Profiler;
import util.enumtypes.ISOLATION_LEVEL;
import util.enumtypes.MODE;

import java.util.Set;

public class GenAntiDepModule implements GenDepModule {
  private Profiler profiler;
  private MODE mode;
  private Config cfg;

  public GenAntiDepModule(Profiler profiler, MODE mode, Config cfg) {
    this.profiler = profiler;
    this.mode = mode;
    this.cfg = cfg;
  }

  @Override
  public InCompleteGraph generate(GraphInputs inputs, InCompleteGraph G) {
    // implies: only for processing those non-certain reads, i.e., may read from multiple write txns.
    assert G instanceof BoomslangGraph;
    var g = (BoomslangGraph) G;
    var utility = new ModuleUtility(profiler, mode, cfg);
    for (var directDep : inputs.directDeps()) {
      Set<Imply> implies = utility.boomslangComputeImply(directDep);

      for (var imply : implies) {
        // skip PRW edges for RR isolation level
        if (mode.spec().ignorePrwEdges() && imply.rwEdge.edgeType == EdgeType.PRW) {
          continue;
        }
        g.addImply(imply);
      }
    }

    return g;
  }
}
