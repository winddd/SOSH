package asg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import common.Key;
import graphs.DirectDep;
import graphs.edges.TypeEdge;
import util.Config;
import util.MapFactory;

public final class DependencyStore {
  private final Set<DirectDep> directDeps;
  private final List<TypeEdge> edges;
  private final Map<Integer, Map<Key, List<DirectDep>>> directDepIndex;
  private final boolean debug;

  public DependencyStore(Config cfg) {
    this.debug = cfg.DEBUG;
    this.directDeps = MapFactory.getEmptySet(debug);
    this.edges = new ArrayList<>();
    this.directDepIndex = new HashMap<>();
  }

  public void addEdge(TypeEdge edge) {
    edges.add(edge);
  }

  public Collection<TypeEdge> edges() {
    return Collections.unmodifiableList(edges);
  }

  public void addDirectDep(DirectDep dep) {
    directDeps.add(dep);
    directDepIndex
        .computeIfAbsent(dep.txnId, k -> new HashMap<>())
        .computeIfAbsent(dep.key, k -> new ArrayList<>())
        .add(dep);
  }

  public Collection<DirectDep> directDeps() {
    return Collections.unmodifiableSet(directDeps);
  }

  public List<DirectDep> directDeps(int txnId, Key key) {
    var byTxn = directDepIndex.get(txnId);
    if (byTxn == null) {
      return List.of();
    }
    var deps = byTxn.get(key);
    if (deps == null) {
      return List.of();
    }
    return List.copyOf(deps);
  }
}
