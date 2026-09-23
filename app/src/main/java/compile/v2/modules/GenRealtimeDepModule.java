package compile.v2.modules;

import compile.v2.inputs.GraphInputs;
import graphs.graphs.InCompleteGraph;
import util.Config;
import util.Profiler;
import util.enumtypes.MODE;

public class GenRealtimeDepModule implements GenDepModule {
  private Profiler profiler;
  private MODE mode;
  private Config cfg;

  public GenRealtimeDepModule(Profiler profiler, MODE mode, Config cfg) {
    this.profiler = profiler;
    this.mode = mode;
    this.cfg = cfg;
  }

  @Override
  public InCompleteGraph generate(GraphInputs inputs, InCompleteGraph graph) {
    return null;
  }
}
