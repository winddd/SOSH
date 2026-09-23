package compile.v2.modules;

import compile.v2.inputs.GraphInputs;
import graphs.graphs.BoomslangGraph;
import graphs.graphs.InCompleteGraph;
import util.Config;
import util.Profiler;
import util.enumtypes.MODE;

public class GenSessionDepModule implements GenDepModule {
  private Profiler profiler;
  private MODE mode;
  private Config cfg;

  public GenSessionDepModule(Profiler profiler, MODE mode, Config cfg) {
    this.profiler = profiler;
    this.mode = mode;
    this.cfg = cfg;
  }

  @Override
  public InCompleteGraph generate(GraphInputs inputs, InCompleteGraph G) {
    assert G instanceof BoomslangGraph;
    var g = (BoomslangGraph) G;

    return g;
  }
}
