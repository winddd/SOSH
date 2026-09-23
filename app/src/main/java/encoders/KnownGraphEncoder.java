package encoders;

import graphs.graphs.InCompleteGraph;
import solvers.KnownGraphSolver;
import solvers.Solver;

import java.util.List;

public class KnownGraphEncoder implements Encoder {
  private final List<InCompleteGraph> graphs;

  public KnownGraphEncoder(List<InCompleteGraph> graphs, String tagPrefix, util.Config cfg) {
    if (graphs == null || graphs.isEmpty()) {
      throw new IllegalArgumentException("KnownGraphEncoder requires at least one graph");
    }
    this.graphs = List.copyOf(graphs);
  }

  @Override
  public Solver encode() {
    return new KnownGraphSolver(graphs);
  }
}
