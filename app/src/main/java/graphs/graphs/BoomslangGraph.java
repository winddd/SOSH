package graphs.graphs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import graphs.edges.TypeEdge;
import org.apache.commons.lang3.NotImplementedException;

import common.Key;
import compile.v2.compilers.RcCompiler;
import compile.v2.compilers.RuCompiler;
import compile.v2.compilers.SerCompiler;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.graphs.interfaces.HasImplies;
import graphs.graphs.interfaces.HasSuperpositions;
import graphs.nodes.GraphNodeId;
import lombok.Getter;
import org.jgrapht.alg.util.Triple;
import util.Config;

/**
 * Primary dependency graph implementation for verifying Serializable (SER),
 * Snapshot Isolation (SI),
 * Read Committed (RC), Read Uncommitted (RU), and PL-2+ isolation levels.
 *
 * <p>
 * BoomslangGraph extends {@link InCompleteGraph} with support for two advanced
 * constraint types:
 * <ul>
 * <li><b>Superpositions:</b> Disjunctive constraints representing "1-out-of-n"
 * edge choices.
 * For example, in SI verification, a read may observe one of multiple possible
 * write versions,
 * encoded as a superposition: (T1→T2 ∨ T3→T2 ∨ T4→T2).</li>
 * <li><b>Implications:</b> Conditional dependencies of the form "if edge A
 * exists, then edge B
 * must exist" (A → B). Used to encode transitive closure requirements and
 * complex ordering
 * constraints in isolation level semantics.</li>
 * </ul>
 *
 * <p>
 * <b>Constraint Semantics:</b>
 * <ul>
 * <li><b>Superposition:</b> At least one edge from each alternative set must be
 * included in
 * any satisfying assignment. This captures non-deterministic version selection
 * in weak
 * isolation models.</li>
 * <li><b>Implication:</b> If the antecedent edge is present, the consequent
 * edge must also
 * be present. Enables encoding of derived dependencies (e.g., write-read
 * transitivity).</li>
 * </ul>
 *
 * <p>
 * <b>Compilation Pipeline Integration:</b> This graph type is produced by
 * mode-specific
 * compilers in the {@code compile.v2} package:
 * <ul>
 * <li>{@link SerCompiler} for Serializable</li>
 * <li>{@link RcCompiler} for Read Committed</li>
 * <li>{@link RuCompiler} for Read Uncommitted</li>
 * </ul>
 *
 * <p>
 * <b>SMT Encoding:</b> Superpositions and implications are translated to SMT
 * constraints
 * by {@link encoders.MonoSATEncoder}, which maps:
 * <ul>
 * <li>Graph nodes → Boolean variables (transaction committed/aborted)</li>
 * <li>Edges → Boolean variables (dependency exists/absent)</li>
 * <li>Superpositions → Disjunctions over edge variables</li>
 * <li>Implications → Boolean implications (antecedent → consequent)</li>
 * </ul>
 *
 * <p>
 * <b>Cycle Detection:</b> The SMT solver searches for satisfying assignments
 * where a cycle
 * exists in the graph, indicating an isolation violation. SAT = violation
 * found, UNSAT = no
 * violation (history satisfies isolation level).
 *
 * <p>
 * <b>Example Usage:</b>
 *
 * <pre>{@code
 * // Create graph for transaction IDs {1, 2, 3}
 * BoomslangGraph graph = new BoomslangGraph(
 *     List.of(GraphNodeId.txn(1), GraphNodeId.txn(2), GraphNodeId.txn(3)),
 *     config);
 *
 * // Add superposition: T2 reads from either T1 or T3 (version choice)
 * graph.addSuperposition(new Superposition(
 *     Set.of(new TypeEdge(1, 2, EdgeType.WR, key)), // Alternative 1: T1 writes, T2 reads
 *     Set.of(new TypeEdge(3, 2, EdgeType.WR, key)) // Alternative 2: T3 writes, T2 reads
 * ));
 *
 * // Add implication: if T1→T2 exists, then T1→T3 must exist (transitivity)
 * graph.addImply(new Imply(
 *     new TypeEdge(1, 2, EdgeType.WW, key),
 *     new TypeEdge(1, 3, EdgeType.WW, key)));
 * }</pre>
 *
 * <p>
 * <b>Thread Safety:</b> Not thread-safe. Graph construction and mutation should
 * occur in
 * a single thread during the compilation phase.
 *
 * @see InCompleteGraph
 * @see graphs.constraints.Superposition
 * @see graphs.constraints.Imply
 * @see HasSuperpositions
 * @see HasImplies
 * @see compile.v2.GraphCompilerFactory
 */
public class BoomslangGraph extends InCompleteGraph implements HasSuperpositions, HasImplies, Serializable {
  protected Set<Superposition> superpositions; // 1 out of n possibilities
  @Getter
  protected List<Imply> implies;
  @Getter
  protected Set<Key> keys;

  /**
   * Constructs a new BoomslangGraph with the specified transaction nodes and
   * configuration.
   *
   * @param nodeIds collection of graph node identifiers (typically transaction
   *                IDs); must not be null
   * @param cfg     configuration controlling graph compilation options
   *                (optimization flags, mode settings, etc.)
   */
  public BoomslangGraph(Collection<GraphNodeId> nodeIds, Config cfg) {
    super(nodeIds, cfg);
    implies = new ArrayList<>();
    superpositions = new HashSet<>();
    keys = Set.of();
  }

  /**
   * Constructs a new BoomslangGraph with the specified transaction nodes,
   * configuration, and key set.
   *
   * @param nodeIds collection of graph node identifiers (typically transaction
   *                IDs); must not be null
   * @param cfg     configuration controlling graph compilation options
   *                (optimization flags, mode settings, etc.)
   * @param keys    set of all keys accessed in the transaction history; must not be null
   */
  public BoomslangGraph(Collection<GraphNodeId> nodeIds, Config cfg, Set<Key> keys) {
    super(nodeIds, cfg);
    implies = new ArrayList<>();
    superpositions = new HashSet<>();
    this.keys = keys != null ? Collections.unmodifiableSet(new HashSet<>(keys)) : Set.of();
  }

  /**
   * Checks if the given node identifier is valid for this graph.
   *
   * <p>
   * Delegates to {@link #isValidTxn(GraphNodeId)} to verify the node represents a
   * valid
   * transaction in the graph's node set.
   *
   * @param nodeId the node identifier to validate
   * @return true if the node is a valid transaction node, false otherwise
   */
  @Override
  public boolean isValidNode(GraphNodeId nodeId) {
    return isValidTxn(nodeId);
  }

  /**
   * Serializes the graph to a file (not implemented).
   *
   * @param filePath destination file path for graph serialization
   * @throws NotImplementedException always thrown; serialization not yet
   *                                 implemented
   */
  @Override
  public void dump(String filePath) {
    throw new NotImplementedException("");
  }

  /**
   * Returns the set of all superposition constraints in this graph.
   *
   * <p>
   * Superpositions represent disjunctive choices in the dependency graph, where
   * at least
   * one edge from each alternative set must be selected in any satisfying
   * assignment.
   *
   * @return unmodifiable view of the superposition constraint set
   */
  @Override
  public Set<Superposition> getSuperpositions() {
    return superpositions;
  }

  /**
   * Adds a superposition constraint to the graph, with special handling for
   * degenerate cases.
   *
   * <p>
   * <b>Degenerate Case Optimization:</b> If the superposition contains only a
   * single edge
   * alternative (i.e., it represents a deterministic dependency), it is converted
   * to a regular
   * edge via {@link Superposition#toTypeEdge()} and added to the graph's edge set
   * instead of
   * being stored as a superposition.
   *
   * <p>
   * <b>Precondition:</b> The superposition must not be empty (assertion
   * enforced).
   *
   * @param ntuple the superposition constraint to add; must not be null or empty
   * @throws AssertionError if the superposition is empty (when assertions
   *                        enabled)
   */
  @Override
  public void addSuperposition(Superposition ntuple) {
    // debug
    // if (ntuple.getEdgeSets().size() == 2 && ntuple.getEdgeSets().get(0).)
    // if (targetSuperpositions.contains(ntuple)) {
    // System.out.println();
    // }
    assert !ntuple.isEmpty();
    // boolean isTrivial = ntuple.isTrivial();
    boolean isAnEdge = ntuple.isAnEdge();

    // if (isTrivial) {
    // return;
    // }

    if (isAnEdge) {
      this.addEdge(ntuple.toTypeEdge());
      return;
    }

    this.superpositions.add(ntuple);
  }

  /**
   * Removes the specified superposition constraint from the graph.
   *
   * @param superposition the superposition to remove; if not present, operation
   *                      is a no-op
   */
  @Override
  public void removeSuperposition(Superposition superposition) {
    superpositions.remove(superposition);
  }

  /**
   * Adds an implication constraint to the graph.
   *
   * <p>
   * An implication represents a conditional dependency: if the antecedent edge
   * exists in
   * the graph, the consequent edge must also exist. Used to encode transitive
   * closures and
   * derived dependencies required by isolation level semantics.
   *
   * @param imply the implication constraint to add; must not be null
   */
  @Override
  public void addImply(Imply imply) {
    implies.add(imply);
  }

  /**
   * Adds multiple implication constraints to the graph.
   *
   * @param implies collection of implication constraints to add; must not be null
   */
  @Override
  public void addAllImplies(Collection<Imply> implies) {
    for (var imply : implies) {
      addImply(imply);
    }
  }

  /**
   * Removes the specified implication constraint from the graph.
   *
   * @param imply the implication to remove; if not present, operation is a no-op
   */
  @Override
  public void removeImply(Imply imply) {
    implies.remove(imply);
  }
}
