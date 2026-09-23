package graphs.graphs;

import common.Key;
import history.KVHistory;
import history.KVTxn;
import graphs.edges.EdgeType;
import graphs.nodes.GraphNodeId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import util.Config;
import util.Profiler;
import util.Utils;
import util.enumtypes.MODE;
import util.isolation.IsolationSpec;
import util.isolation.PruningSpec;

/**
 * Abstract base class for matrix-based dependency graph representations.
 *
 * <p>MatrixGraph provides a dense matrix representation of transaction dependency graphs,
 * where nodes are mapped to integer indices via a {@link NodeIndexer} and edges are stored
 * as matrix entries. This representation is optimized for:
 * <ul>
 *   <li><b>Reachability queries:</b> Constant-time O(1) lookup for edge existence</li>
 *   <li><b>Transitive closure:</b> Efficient matrix multiplication algorithms (e.g., Floyd-Warshall)</li>
 *   <li><b>Dense graphs:</b> Space-efficient when most node pairs have dependencies</li>
 *   <li><b>Numerical algorithms:</b> Integration with linear algebra libraries (ND4J)</li>
 * </ul>
 *
 * <p><b>Node Indexing:</b> Nodes are mapped to dense integer indices [0, n) via {@link NodeIndexer},
 * which provides stable ordering based on node kind, transaction ID, and operation index. This
 * enables matrix indices to directly correspond to node identifiers.
 *
 * <p><b>Matrix Implementations:</b> Concrete subclasses implement the actual storage backend:
 * <ul>
 *   <li>{@code ArrayMatrixGraph} - Java 2D array (simple, moderate performance)</li>
 *   <li>{@code BitmapMatrixGraph} - Roaring bitmap (memory-efficient for sparse graphs)</li>
 *   <li>{@code NdArrayMatrixGraph} - ND4J matrix (high-performance, GPU-accelerated)</li>
 * </ul>
 *
 * <p><b>Edge Reduction Optimization:</b> When session ordering is enabled (multiple transactions
 * in the same session), the {@link #buildFromGraphForPruning(InCompleteGraph, Config)} method applies an
 * optimization that reduces redundant edges:
 * <ul>
 *   <li>For each node u and each session s, only add an edge to the <i>first</i> successor
 *       transaction in session s (earliest in session order)</li>
 *   <li>This preserves reachability while reducing edge count, accelerating solver performance</li>
 *   <li>Direct session-order edges (consecutive transactions in same session) are always preserved</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * // Create indexer for transaction nodes
 * NodeIndexer indexer = NodeIndexer.from(graph.getNodes());
 *
 * // Instantiate concrete matrix graph (e.g., ArrayMatrixGraph)
 * MatrixGraph matrixGraph = new ArrayMatrixGraph(indexer);
 *
 * // Populate from incomplete graph
 * matrixGraph.buildFromGraph(incompleteGraph, config);
 *
 * // Query reachability
 * GraphNodeId nodeA = GraphNodeId.txn(1);
 * GraphNodeId nodeB = GraphNodeId.txn(5);
 * boolean hasPath = matrixGraph.reach(
 *     matrixGraph.indexOfNode(nodeA),
 *     matrixGraph.indexOfNode(nodeB)
 * );
 * }</pre>
 *
 * @see NodeIndexer
 * @see InCompleteGraph
 */
public abstract class MatrixGraph {
  protected final Profiler profiler;
  @Getter
  private final NodeIndexer indexer;

  /**
   * Constructs a new MatrixGraph with the specified node indexer.
   *
   * @param indexer node indexer providing stable node-to-index mapping; must not be null
   * @throws NullPointerException if indexer is null
   */
  protected MatrixGraph(NodeIndexer indexer) {
    this.indexer = Objects.requireNonNull(indexer, "indexer");
    this.profiler = Profiler.getInstance();
  }

  /**
   * Sets the matrix entry at position (i, j) to indicate an edge from node i to node j.
   *
   * <p>Implementation-specific: the value may be a boolean flag, edge count, or edge weight.
   *
   * @param i source node index (0-based)
   * @param j destination node index (0-based)
   */
  public abstract void set(int i, int j);

  /**
   * Returns the value of the matrix entry at position (i, j).
   *
   * <p>A non-zero value indicates an edge exists from node i to node j. The specific
   * value interpretation depends on the implementation (e.g., 1 for boolean, edge count, etc.).
   *
   * @param i source node index (0-based)
   * @param j destination node index (0-based)
   * @return integer value representing edge presence/weight; 0 indicates no edge
   */
  public abstract int get(int i, int j);

  /**
   * Checks if node v is reachable from node u (edge u → v exists).
   *
   * @param u source node index (0-based)
   * @param v destination node index (0-based)
   * @return true if an edge from u to v exists (get(u, v) &gt; 0), false otherwise
   */
  public boolean reach(int u, int v) {
    return this.get(u, v) > 0;
  }

  /**
   * Returns the matrix index corresponding to the given node identifier.
   *
   * @param nodeId the node identifier to look up
   * @return 0-based matrix index for the node
   * @throws IllegalArgumentException if the node is not indexed
   */
  public int indexOfNode(GraphNodeId nodeId) {
    return indexer.requireIndex(nodeId);
  }

  /**
   * Returns the total number of nodes in the matrix (matrix dimension).
   *
   * @return number of nodes (matrix is size() × size())
   */
  public int size() {
    return indexer.size();
  }

  /**
   * Populates this matrix graph from an {@link InCompleteGraph}, with optional edge reduction.
   *
   * <p>This method converts an adjacency-list-based graph representation to a dense matrix
   * representation, applying optimizations based on the configuration:
   *
   * <p><b>Standard Mode (no optimization):</b> All edges from the adjacency list are copied
   * directly to the matrix via {@link #populateDirect(Map)}.
   *
   * <p><b>Edge Reduction Mode (when enabled):</b> If the following conditions are met:
   * <ul>
   *   <li>Graph contains only transaction nodes (no action/operation nodes)</li>
   *   <li>Session ordering is enabled ({@code cfg.SESSION_ORDER})</li>
   *   <li>Edge reduction is enabled ({@code cfg.ENABLE_EDGES_REDUCTION})</li>
   *   <li>Run mode is not Viper or B_SI</li>
   * </ul>
   * then an optimization is applied that reduces redundant edges while preserving reachability:
   * <ul>
   *   <li>For each source node u and each session s, only add an edge to the <i>earliest</i>
   *       successor transaction in session s (first in session order)</li>
   *   <li>Always add direct session-order edges (consecutive transactions in same session)</li>
   *   <li>This reduces edge count without affecting cycle detection, accelerating solver performance</li>
   * </ul>
   *
   * <p><b>Rationale:</b> If u → v1 and u → v2 where v1 and v2 are in the same session and
   * v1 precedes v2 in session order, then the edge u → v2 is redundant for reachability
   * purposes (path u → v1 → v2 exists via session order). Omitting such edges reduces
   * the problem size for the solver.
   *
   * @param graph the incomplete graph to convert; must not be null
   * @param cfg configuration controlling optimization behavior; must not be null
   * @throws RuntimeException if edge reduction encounters a successor node not in the indexer
   */
  public void buildFromGraphForPruning(InCompleteGraph graph, Config cfg) {
//    profiler.startTick("build matrix graph from Incompletegraph");
    var adjList = graph.getAdjList();
    var history = graph.getHistory();
    boolean txnOnlyGraph = indexer.isTxnOnly();
    PruningSpec pruningSpec = cfg.getIsolationSpec().getPruningSpec();

    if (txnOnlyGraph && cfg.SESSION_ORDER && cfg.ENABLE_EDGES_REDUCTION
        && cfg.RUNMODE != MODE.V && cfg.RUNMODE != MODE.B_SI) {
      populateWithSessionReduction(history, adjList, pruningSpec);
    } else {
      populateDirect(adjList, pruningSpec);
    }
//    profiler.endTick("graph2javaMatrix");
//    profiler.endTick("build matrix graph from Incompletegraph");
  }

  private void populateWithSessionReduction(KVHistory history,
                                            Map<GraphNodeId, Map<GraphNodeId,
                                            Set<Pair<EdgeType, Key>>>> adjList,
                                            PruningSpec pruningSpec) {
    // TODO: make it work for SI.
    // if with session order, we can possibly apply an optimization of reducing edges without affecting reachability.
    // the rank of each txn in its own session.
    Map<KVTxn, Integer> orderInSession = Utils.getOrderInSession(history);

    for (var uid : adjList.keySet()) {
      Map<Long, KVTxn> session2FirstSucc = new HashMap<>();
      // successors of node `uid`
      var successorTxnIds = adjList.get(uid).keySet().stream()
        .filter(vid -> hasRelevantEdge(adjList.get(uid).get(vid), pruningSpec))
        .collect(Collectors.toSet());

      // Set `session2FirstSucc`.
      for (var vid : successorTxnIds) {
        var v = history.getKthTxn(vid.txnId());
        var sessionId = v.getThreadId();

        // If the rank of node v is smaller than the existing one in `session2FirstSucc`, update `session2FirstSucc`.
        if (session2FirstSucc.containsKey(sessionId)) {
          if (orderInSession.get(v) < orderInSession.get(session2FirstSucc.get(sessionId))) {
            session2FirstSucc.put(sessionId, v);
          }
        } else {
          session2FirstSucc.put(sessionId, v);
        }
      }

      // Now that we hae already set `session2FirstSucc`,
      // then we add edges from txn u to the first successor txn in each session.
      int encodedUid = indexOfNode(uid);
      for (var tid : session2FirstSucc.keySet()) {
        GraphNodeId succNode = GraphNodeId.txn((int) session2FirstSucc.get(tid).getTxnId());
        if (!indexer.contains(succNode)) {
          throw new RuntimeException("Successor node not indexed: " + succNode);
        }
        this.set(encodedUid, indexOfNode(succNode));
      }

      // add direct SO edges
      int encodedUid2 = indexOfNode(uid);
      var u = history.getKthTxn(uid.txnId());
      for (var vid : successorTxnIds) {
        int encodedVid2 = indexOfNode(vid);
        var v = history.getKthTxn(vid.txnId());
        // debugging
//          if (orderInSession.get(u) == null || orderInSession.get(v) == null) {
//            System.out.println();
//          }
        if (u.getThreadId() == v.getThreadId()
          && orderInSession.get(u) + 1 == orderInSession.get(v)) {
          this.set(encodedUid2, encodedVid2);
        }
      }
    }
  }

  private boolean hasRelevantEdge(Set<Pair<EdgeType, Key>> edgeLabels,
                                  PruningSpec pruningSpec) {
    return edgeLabels.stream()
      .anyMatch(p -> pruningSpec.includeForPruning(p.getLeft()));
  }

  /**
   * Populates the matrix by directly copying edges from the adjacency list.
   *
   * <p>For each edge (u, v) in the adjacency list with at least one edge type included
   * by the pruning spec, sets the matrix entry at (indexOfNode(u), indexOfNode(v)).
   *
   * @param adjList adjacency list mapping source nodes to destination nodes to edge labels
   * @param pruningSpec pruning specification used to filter edge types
   */
  private void populateDirect(Map<GraphNodeId, Map<GraphNodeId, Set<Pair<EdgeType, Key>>>> adjList,
                              PruningSpec pruningSpec) {
    for (GraphNodeId u : adjList.keySet()) {
      int encodedU = indexOfNode(u);

      for (GraphNodeId v : adjList.get(u).keySet()) {
        // Skip self loops for RYOW
        if (u.equals(v)) {
          continue;
        }

        int encodedV = indexOfNode(v);
        if (hasRelevantEdge(adjList.get(u).get(v), pruningSpec)) {
          this.set(encodedU, encodedV);
        }
      }
    }
  }
}
