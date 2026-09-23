package compile.v2.modules;
import compile.v2.inputs.GraphInputs;
import graphs.graphs.InCompleteGraph;

public interface GenDepModule {
  InCompleteGraph generate(GraphInputs inputs, InCompleteGraph g);
}
