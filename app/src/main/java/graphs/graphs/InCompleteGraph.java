package graphs.graphs;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import common.Key;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.interfaces.HasGeneralConstraints;
import graphs.graphs.interfaces.HasImplies;
import graphs.graphs.interfaces.HasSuperpositions;
import history.KVHistory;
import lombok.Getter;
import lombok.Setter;
import graphs.nodes.GraphNodeId;
import graphs.nodes.NodeKind;
import org.jgrapht.alg.util.Triple;
import util.Config;
import util.MapFactory;
import util.enumtypes.AdyaGraphType;
import util.exception.RejectException;


/**
 * Abstract base class for dependency graphs with known edges and optional constraints.
 *
 * <p>InCompleteGraph represents a directed graph where:
 * <ul>
 *   <li><b>Nodes ({@code V}):</b> Transaction nodes ({@link GraphNodeId}) or other node types depending on subclass</li>
 *   <li><b>Known Edges:</b> Typed edges ({@link TypeEdge}) stored in an adjacency list</li>
 *   <li><b>Constraints (optional):</b> Superpositions, implications, or other general constraints</li>
 * </ul>
 *
 * <p><b>Graph Structure:</b>
 * <ul>
 *   <li>{@code nodes}: Set of all graph nodes</li>
 *   <li>{@code adjList}: Adjacency list mapping source → target → set of (EdgeType, Key) pairs</li>
 *   <li>Multiple edges between the same pair of nodes are allowed if they have different types or keys</li>
 * </ul>
 *
 * <p><b>Constraint Modeling:</b>
 * Subclasses can implement:
 * <ul>
 *   <li>{@link HasSuperpositions}: Disjunctive constraints (choose one edge set from multiple options)</li>
 *   <li>{@link HasImplies}: Implication constraints (if edge A exists, then edge B must exist)</li>
 *   <li>{@link HasGeneralConstraints}: Other constraint types</li>
 * </ul>
 *
 * <p><b>Primary Subclasses:</b>
 * <ul>
 *   <li>{@link BoomslangGraph}: Standard graph for SER/SI/RC/RU/PL2+ checking</li>
 *   <li>{@link BCPolygraph}: Graph with begin-commit node pairs for more precise modeling</li>
 *   <li>{@link PolySIGraph}: Graph for PolySI-specific verification</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> NOT thread-safe. Graphs should be built sequentially,
 * then passed to solvers.
 *
 * @see TypeEdge for edge representation
 * @see GraphNodeId for node representation
 * @see encoders.MonoSATEncoder for SMT encoding of graphs
 */
public abstract class InCompleteGraph implements Serializable {
  protected Set<GraphNodeId> nodes;
  protected Map<GraphNodeId, Map<GraphNodeId, Set<Pair<EdgeType, Key>>>> adjList;
  @Getter
  @Setter
  protected KVHistory history;
  protected Config cfg;
  @Getter
  @Setter
  protected AdyaGraphType graphType;
  //  protected Set<TypeEdge> edges;
  // debug
  List<Integer> cycle = List.of();
  //  List<Integer> cycle = List.of(306, 307, 1016, 1017, 2020, 2021, 166, 167, 1190, 1191, 1628, 1629, 1460, 1461, 155, 1613, 3638, 3639, 910, 911, 76, 77, 306);
//  Set<Pair<Integer, Integer>> targetEdges = new HashSet<>(List.of());
  // debug
  private Set<Triple<Integer, Integer, String>> targetEdges = new HashSet<>(List.of(Triple.of(7489, 6358, "gAAAAAAARqg="),
    Triple.of(6358, 7489, "gAAAAAAARqY=")));

  public InCompleteGraph(Collection<GraphNodeId> nodeIds, Config cfg) {
    Objects.requireNonNull(nodeIds, "nodeIds");
    this.cfg = cfg;
    this.nodes = new HashSet<>();
    adjList = MapFactory.getEmptyMap(cfg.DEBUG);
    for (GraphNodeId nodeId : nodeIds) {
      ensureNode(nodeId);
    }

    // debug
//    if (cfg.DEBUG) {
//      for (int i = 0; i < cycle.size() - 1; i++) {
//        int next = (i + 1) % cycle.size();
//        targetEdges.add(Pair.of(cycle.get(i), cycle.get(next)));
//      }
//    }
  }

  /**
   * Returns counts of edges in the graph.
   *
   * @return pair where left=total typed edges (different types count separately),
   *         right=unique node pairs (ignoring edge types)
   */
  public Pair<Integer, Integer> numOfEdges() {
    int numEdges = 0; // different types of edges count
    int uniqueEdges = 0; // ignore edge types
    for (var u : adjList.keySet()) {
      uniqueEdges += adjList.get(u).size();

      for (var v : adjList.get(u).keySet()) {
        numEdges += adjList.get(u).get(v).size();
      }
    }

    return Pair.of(numEdges, uniqueEdges);
  }

  /**
   * Check if a GraphNodeId represents a valid transaction node in this graph.
   * For InCompleteGraph, this is the same as isValidNode.
   *
   * @param nodeId
   * @return
   */
  public boolean isValidTxn(GraphNodeId nodeId) {
    Objects.requireNonNull(nodeId, "nodeId");
    return nodes.contains(nodeId);
  }

  public boolean isValidTxn(GraphNodeId... nodeIds) {
    boolean ret = true;
    for (var nodeId : nodeIds) {
      if (!isValidTxn(nodeId)) {
        ret = false;
        break;
      }
    }

    return ret;
  }

  public boolean isValidNode(GraphNodeId nodeId) {
    Objects.requireNonNull(nodeId, "nodeId");
    return nodes.contains(nodeId);
  }

  /*
   * BCPolygraph should override this function to return `nodes` instead of
   * `txns`.
   */
  public Set<GraphNodeId> getNodeIds() {
    return java.util.Collections.unmodifiableSet(nodes);
  }

  /**
   * Returns transaction IDs from the node set.
   * This is a convenience method that extracts txnId() from each GraphNodeId.
   * Throws RuntimeException if any node is not of TXN kind.
   */
  public Set<Integer> getNodes() {
    return nodes.stream()
        .peek(nodeId -> {
          if (nodeId.kind() != NodeKind.TXN) {
            throw new RuntimeException("getNodes() called but graph contains non-TXN node: " + nodeId + " of kind " + nodeId.kind());
          }
        })
        .map(GraphNodeId::txnId)
        .collect(java.util.stream.Collectors.toSet());
  }


  /**
   * Adds a typed edge to the graph.
   *
   * <p>The edge is added to the adjacency list. Multiple edges between the same nodes
   * are allowed if they have different types or keys.
   *
   * @param edge the typed edge to add
   * @throws RuntimeException if edge references non-existent nodes
   * @throws RejectException if edge would create a self-loop
   */
  public void addEdge(TypeEdge edge) {
//    if (isTargeted(edge)) {
//      System.out.println();
//    }
    GraphNodeId sourceId = edge.getSourceId();
    GraphNodeId targetId = edge.getTargetId();

    if (!isValidEdge(edge)) {
      String sourceKind = sourceId.kind().toString();
      String targetKind = targetId.kind().toString();
      boolean sourceValid = nodes.contains(sourceId);
      boolean targetValid = nodes.contains(targetId);
      throw new RuntimeException(String.format(
          "Invalid edge: source=%s (kind=%s, valid=%s), target=%s (kind=%s, valid=%s)",
          sourceId, sourceKind, sourceValid, targetId, targetKind, targetValid));
    }

    if (!adjList.containsKey(sourceId)) {
      adjList.put(sourceId, MapFactory.getEmptyMap(cfg.DEBUG));
    }
    if (!adjList.get(sourceId).containsKey(targetId)) {
      adjList.get(sourceId).put(targetId, MapFactory.getEmptySet(cfg.DEBUG));
    }

    // update adjList
    adjList.get(sourceId).get(targetId).add(Pair.of(edge.edgeType, edge.key));
  }

  /**
   * Adds a typed edge specified by components.
   *
   * @param sourceId the source node
   * @param targetId the target node
   * @param edgeType the edge type (WR, WW, RW, etc.)
   * @param key the key associated with this dependency
   */
  public void addEdge(GraphNodeId sourceId, GraphNodeId targetId, EdgeType edgeType, Key key) {
    addEdge(new TypeEdge(sourceId, targetId, edgeType, key));
  }

  private void ensureNode(GraphNodeId nodeId) {
    if (nodes.contains(nodeId)) {
      return;
    }

    // Add node to the set
    nodes.add(nodeId);

    // Ensure adjacency list entry exists
    adjList.computeIfAbsent(nodeId, __ -> MapFactory.getEmptyMap(cfg.DEBUG));
  }


  /**
   * Checks if an edge is valid (both endpoints exist in the graph).
   *
   * @param edge the edge to validate
   * @return true if both source and target nodes exist
   */
  public boolean isValidEdge(TypeEdge edge) {
    return isValidNode(edge.getSourceId()) && isValidNode(edge.getTargetId());
  }

  /**
   * Adds multiple edges to the graph.
   *
   * @param edges collection of typed edges to add
   */
  public void addEdges(Collection<TypeEdge> edges) {
    for (var edge : edges) {
      this.addEdge(edge);
    }
  }

  /**
   * Returns the adjacency list representation of the graph.
   *
   * @return map from source → target → set of (EdgeType, Key) pairs
   */
  public Map<GraphNodeId, Map<GraphNodeId, Set<Pair<EdgeType, Key>>>> getAdjList() {
    return this.adjList;
  }

  /**
   * Dumps the graph to a file for debugging/visualization.
   *
   * @param filePath the output file path
   */
  public abstract void dump(String filePath);

  /**
   * Returns the number of general constraints if the graph supports them.
   *
   * @return constraint count, or 0 if graph doesn't implement {@link HasGeneralConstraints}
   */
  public int numGeneralCons(){
    int n = 0;
    if (this instanceof HasGeneralConstraints) {
      n = ((HasGeneralConstraints) this).getGeneralCons().size();
    }
    return n;
  }

  /**
   * Returns the number of superposition constraints if the graph supports them.
   *
   * @return superposition count, or 0 if graph doesn't implement {@link HasSuperpositions}
   */
  public int numSuperpositions(){
    int n = 0;
    if (this instanceof HasSuperpositions) {
      n = ((HasSuperpositions) this).getSuperpositions().size();
    }
    return n;
  }

  /**
   * Returns the number of implication constraints if the graph supports them.
   *
   * @return implication count, or 0 if graph doesn't implement {@link HasImplies}
   */
  public int numImplies() {
    int n = 0;
    if (this instanceof HasImplies) {
      n = ((HasImplies) this).getImplies().size();
    }
    return n;
  }


  private boolean isTargeted(TypeEdge e) {
    for (var triple : targetEdges) {
      if (GraphNodeId.txn(triple.getFirst()).equals(e.getSourceId()) && GraphNodeId.txn(triple.getSecond()).equals(e.getTargetId()) && triple.getThird().equals(e.key.toString())) {
        return true;
      }
    }
    return false;
  }
}
