package graphs.graphs.interfaces;

import com.google.common.graph.MutableValueGraph;
import common.Key;
import graphs.edges.EdgeType;
import history.KVHistory;
import java.util.Collection;
import org.apache.commons.lang3.tuple.Pair;

public interface PolySIABGraph extends HasSuperpositions {
  MutableValueGraph<Integer, Collection<Pair<EdgeType, Key>>> getGraphA();

  MutableValueGraph<Integer, Collection<Pair<EdgeType, Key>>> getGraphB();

  KVHistory getHistory();
}
