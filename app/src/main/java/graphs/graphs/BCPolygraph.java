package graphs.graphs;

import graphs.constraints.Superposition;
import graphs.edges.TypeEdge;
import graphs.graphs.interfaces.HasSuperpositions;
import graphs.nodes.GraphNodeId;
import graphs.nodes.NodeKind;
import org.apache.commons.lang3.NotImplementedException;
import util.CommonUtils;
import util.Config;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Specialized dependency graph for Viper's begin-commit (BC) based isolation verification.
 *
 * <p>BCPolygraph extends the standard {@link InCompleteGraph} with support for begin-commit
 * node pairs and boolean constraint expressions over edge variables. Unlike traditional
 * transaction-level graphs where each transaction is represented by a single node, BC graphs
 * model each transaction T<sub>i</sub> as a pair of nodes:
 * <ul>
 *   <li><b>b<sub>i</sub>:</b> Begin node representing transaction start</li>
 *   <li><b>c<sub>i</sub>:</b> Commit node representing transaction commit</li>
 * </ul>
 *
 * <p><b>Node Semantics:</b> Dependencies can be encoded between begin/commit nodes to capture
 * fine-grained ordering constraints. For example:
 * <ul>
 *   <li>c<sub>i</sub> → b<sub>j</sub>: Transaction T<sub>i</sub> must commit before
 *       T<sub>j</sub> begins (non-overlapping execution)</li>
 *   <li>b<sub>i</sub> → c<sub>i</sub>: Internal ordering constraint (begin precedes commit)</li>
 *   <li>c<sub>i</sub> → c<sub>j</sub>: Commit ordering (T<sub>i</sub> commits before
 *       T<sub>j</sub>)</li>
 * </ul>
 *
 * <p><b>Superposition Constraints:</b> The "Poly" prefix indicates support for superpositions
 * (disjunctive edge constraints), extended to allow arbitrary boolean expressions over edge
 * variables. This enables encoding complex version selection and visibility rules required
 * by weak isolation levels.
 *
 * <p><b>Validation:</b> Transaction validation in BC graphs requires both the begin and commit
 * nodes to be present in the node set. See {@link #isValidTxn(GraphNodeId)} for details.
 *
 * <p><b>Edge Constraints:</b> All edges added to this graph must be BC-compatible edges
 * (connecting begin/commit nodes), validated via {@link TypeEdge#isBCEdge()}.
 *
 * <p><b>Use Case:</b> This graph type is specific to the Viper verification tool and is
 * typically constructed by Viper-specific compilers that model transaction execution as
 * begin-commit intervals.
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * // Transaction IDs: 1, 2, 3
 * // Nodes: b1, c1, b2, c2, b3, c3
 * BCPolygraph graph = new BCPolygraph(
 *     List.of(
 *         GraphNodeId.txn(bi(1)), GraphNodeId.txn(ci(1)),
 *         GraphNodeId.txn(bi(2)), GraphNodeId.txn(ci(2)),
 *         GraphNodeId.txn(bi(3)), GraphNodeId.txn(ci(3))
 *     ),
 *     config
 * );
 *
 * // T1 commits before T2 begins (non-overlapping)
 * graph.addEdge(new TypeEdge(ci(1), bi(2), EdgeType.SO, key));
 * }</pre>
 *
 * @see InCompleteGraph
 * @see graphs.constraints.Superposition
 * @see HasSuperpositions
 * @see util.CommonUtils#bi(int)
 * @see util.CommonUtils#ci(int)
 */
// the meaning of Poly is extended to allow arbitrary boolean expressions
public class BCPolygraph extends InCompleteGraph implements HasSuperpositions {
  // private int nNodes; // index from 0
  // private Set<Integer> txns;
  // private Map<Integer, Set<Integer>> edges;
  // Map<Integer, Map<Integer, Set<EdgeType>>> edges;
  // private Set<Integer> nodes;
  // protected Set<EdgePair> cons; // 1 out of 2 possibilities
  private Set<Superposition> superpositions;

  /**
   * Constructs a new BCPolygraph with the specified begin-commit node pairs and configuration.
   *
   * <p>The nodes collection should contain both begin (b<sub>i</sub>) and commit (c<sub>i</sub>)
   * nodes for each transaction T<sub>i</sub>.
   *
   * @param nodes collection of graph node identifiers (begin/commit node pairs); must not be null
   * @param cfg configuration controlling graph compilation options
   */
  public BCPolygraph(Collection<GraphNodeId> nodes, Config cfg) { // numTxns includes T0
    super(nodes, cfg);
    superpositions = new HashSet<>();
  }

  /**
   * Returns the set of all superposition constraints in this graph.
   *
   * @return set of superposition constraints (disjunctive edge choices)
   */
  @Override
  public Set<Superposition> getSuperpositions() {
    return superpositions;
  }

  /**
   * Adds a superposition constraint to the graph, with BC-specific validation.
   *
   * <p><b>Precondition:</b> The superposition must be a valid BC n-tuple, verified via
   * {@link Superposition#isBCNTuple()} (assertion enforced).
   *
   * <p><b>Degenerate Case:</b> If the superposition represents a single deterministic edge,
   * it is added to the graph's edge set instead of being stored as a superposition.
   *
   * @param superposition the superposition constraint to add; must be a valid BC n-tuple
   * @throws AssertionError if the superposition is not a BC n-tuple (when assertions enabled)
   */
  @Override
  public void addSuperposition(Superposition superposition) {
    assert superposition.isBCNTuple();
    if (superposition.isAnEdge()) {
      addEdge(superposition.toTypeEdge());
      return;
    }
    superpositions.add(superposition);
  }

  /**
   * Removes the specified superposition constraint from the graph.
   *
   * @param superposition the superposition to remove; if not present, operation is a no-op
   */
  @Override
  public void removeSuperposition(Superposition superposition) {
    superpositions.remove(superposition);
  }

  /**
   * Validates whether a transaction node ID is valid in this BC graph.
   *
   * <p>For BC graphs, a transaction is valid if and only if both its begin node (b<sub>i</sub>)
   * and commit node (c<sub>i</sub>) are present in the graph's node set. This ensures
   * transactions are represented as complete begin-commit intervals.
   *
   * @param nodeId the transaction node ID to validate; must be a transaction node
   * @return true if both begin and commit nodes for this transaction exist in the graph
   * @throws AssertionError if nodeId is not a transaction node (when assertions enabled)
   */
  @Override
  public boolean isValidTxn(GraphNodeId nodeId) {
    assert nodeId.isTxn();
    int tid = nodeId.txnId();
    GraphNodeId biNodeId = GraphNodeId.txn(CommonUtils.bi(tid));
    GraphNodeId ciNodeId = GraphNodeId.txn(CommonUtils.ci(tid));
    return this.nodes.contains(biNodeId) && this.nodes.contains(ciNodeId);
  }

  /**
   * Adds a directed edge to the graph, with BC-specific validation.
   *
   * <p>All edges added to BC graphs must be BC-compatible edges connecting begin/commit nodes.
   * The edge is validated via {@link TypeEdge#isBCEdge()} (assertion enforced).
   *
   * @param edge the edge to add; must be a BC-compatible edge
   * @throws AssertionError if the edge is not BC-compatible (when assertions enabled)
   */
  public void addEdge(TypeEdge edge) {
    assert edge.isBCEdge();
    super.addEdge(edge);
  }

  /**
   * Serializes the graph to a file (not implemented).
   *
   * @param filePath destination file path for graph serialization
   * @throws NotImplementedException always thrown; serialization not yet implemented
   */
  @Override
  public void dump(String filePath) {
    throw new NotImplementedException();
  }
}
