package compile.v2.modules;
import asg.ReadIndex;
import compile.v2.inputs.GraphInputs;
import graphs.graphs.BoomslangGraph;
import graphs.graphs.InCompleteGraph;
import util.Config;
import util.Profiler;
import util.enumtypes.MODE;

public class GenWriteDepModule implements GenDepModule {
  private Profiler profiler;
  private MODE mode;
  private Config cfg;

  public GenWriteDepModule(Profiler profiler, MODE mode, Config cfg) {
    this.profiler = profiler;
    this.mode = mode;
    this.cfg = cfg;
  }

  @Override
  public InCompleteGraph generate(GraphInputs inputs, InCompleteGraph G) {
    assert G instanceof BoomslangGraph;
    var g = (BoomslangGraph) G;
    var utility = new ModuleUtility(profiler, mode, cfg);

    // Build ReadIndex on-demand from direct dependencies
    var readIndex = ReadIndex.from(inputs.directDeps());
    var readFrom = readIndex.readFrom();
    var vo = inputs.versionOrder();
    var skipPrwEdges = mode.spec().ignorePrwEdges();
    var knownWwRwEdges = utility.boomslangComputeKnownWwRwEdges(inputs.writes().getExtWrites().keySet(), vo, readFrom, skipPrwEdges);
    for (var edge : knownWwRwEdges) {
      g.addEdge(edge);
    }

    var superpositionsAndEdges = utility.boomslangComputeUnknownWwRw(inputs.writes().getExtWrites().keySet(), vo, readFrom, skipPrwEdges);
    for (var edge: superpositionsAndEdges.getRight()) {
      g.addEdge(edge);
    }

    for (var superposition : superpositionsAndEdges.getLeft()) {
      g.addSuperposition(superposition);
    }

    return g;
  }
}
