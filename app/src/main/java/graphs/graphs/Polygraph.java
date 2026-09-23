package graphs.graphs;

import common.Key;
import graphs.constraints.Superposition;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.interfaces.HasSuperpositions;
import graphs.nodes.GraphNodeId;
import graphs.nodes.NodeKind;
import org.apache.commons.lang3.tuple.Pair;
import util.Config;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Collection;
import java.util.Set;

/**
 * Dependency graph implementation for the Cobra transactional verification tool.
 *
 * <p>Polygraph extends {@link InCompleteGraph} with support for superposition constraints
 * (disjunctive edge choices) and provides Cobra-specific serialization capabilities. The name
 * "Polygraph" reflects support for "polymorphic" edge constraints where multiple alternative
 * dependency edges can be represented via superpositions.
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li><b>Superpositions:</b> Disjunctive constraints representing "1-out-of-n" edge choices,
 *       enabling encoding of non-deterministic version selection in weak isolation models</li>
 *   <li><b>Edge Tracking:</b> Maintains an explicit edge list for efficient iteration and
 *       serialization</li>
 *   <li><b>Statistics:</b> Provides {@link #getStatistics()} method for profiling graph size
 *       and complexity metrics</li>
 *   <li><b>Serialization:</b> Implements {@link #dump(String)} to export graph structure to
 *       Cobra-compatible format</li>
 * </ul>
 *
 * <p><b>Graph Format (Serialization):</b> The {@code dump()} method outputs a text-based
 * representation used by Cobra:
 * <pre>
 * &lt;numNodes&gt;
 * e:&lt;srcId&gt;,&lt;dstId&gt;,&lt;edgeType&gt;
 * e:&lt;srcId&gt;,&lt;dstId&gt;,&lt;edgeType&gt;
 * ...
 * </pre>
 * Each edge is encoded as a triple of (source node, destination node, edge type).
 *
 * <p><b>Node Encoding:</b> Transaction nodes are encoded as their numeric transaction IDs.
 * See {@link #encodeNode(GraphNodeId)} for the encoding scheme.
 *
 * <p><b>Use Case:</b> This graph type is specific to the Cobra verification tool and is
 * typically constructed by Cobra-specific compilers that target Cobra's input format and
 * constraint language.
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * Polygraph graph = new Polygraph(
 *     List.of(GraphNodeId.txn(1), GraphNodeId.txn(2), GraphNodeId.txn(3)),
 *     config
 * );
 *
 * // Add write-read dependency with version choice
 * graph.addSuperposition(new Superposition(
 *     Set.of(new TypeEdge(1, 3, EdgeType.WR, key1)),  // T3 reads from T1
 *     Set.of(new TypeEdge(2, 3, EdgeType.WR, key1))   // or T3 reads from T2
 * ));
 *
 * // Serialize to file for Cobra processing
 * graph.dump("/tmp/cobra_graph.txt");
 * }</pre>
 *
 * @see InCompleteGraph
 * @see graphs.constraints.Superposition
 * @see HasSuperpositions
 */
public class Polygraph extends InCompleteGraph implements HasSuperpositions {
  // private int nNodes;
  // edges: u->v-> all the edges from u to v
  // act as a cache of all the edges
  Set<Superposition> superpositions;
  private List<TypeEdge> edgeList;

  /**
   * Constructs a new Polygraph with the specified transaction nodes and configuration.
   *
   * <p>The number of nodes typically includes transaction T<sub>0</sub> (the initial state).
   *
   * @param nodes collection of graph node identifiers (transaction IDs); must not be null
   * @param cfg configuration controlling graph compilation options
   */
  public Polygraph(Collection<GraphNodeId> nodes, Config cfg) {
    super(nodes, cfg);
    edgeList = new ArrayList<>();
    superpositions = new HashSet<>();
  }

  /**
   * Encodes a graph node identifier as an integer for serialization.
   *
   * <p>For transaction nodes, returns the numeric transaction ID.
   *
   * @param nodeId the node identifier to encode; must be a transaction node
   * @return the transaction ID as an integer
   * @throws AssertionError if nodeId is not a transaction node (when assertions enabled)
   */
  private static int encodeNode(GraphNodeId nodeId) {
    assert nodeId.kind() == NodeKind.TXN;
    return nodeId.txnId();
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
   * Adds a superposition constraint to the graph, with special handling for degenerate cases.
   *
   * <p><b>Degenerate Case Optimization:</b> If the superposition represents a single
   * deterministic edge (contains only one alternative), it is added to the graph's edge set
   * instead of being stored as a superposition.
   *
   * @param superposition the superposition constraint to add; must not be null
   */
  @Override
  public void addSuperposition(Superposition superposition) {
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
   * Computes and returns graph statistics for profiling and analysis.
   *
   * <p>The statistics include:
   * <ol>
   *   <li>Number of nodes</li>
   *   <li>Number of typed edges (left component of edge count)</li>
   *   <li>Number of key-specific edges (right component of edge count)</li>
   *   <li>Number of superposition constraints</li>
   *   <li>Total number of boolean operators (currently always 0)</li>
   * </ol>
   *
   * @return list of five integers representing graph complexity metrics
   */
  public List<Integer> getStatistics() {
    var numEdges = numOfEdges();
    int totalOperators = 0;

    return List.of(nodes.size(), numEdges.getLeft(), numEdges.getRight(),
        superpositions.size(), totalOperators);
  }

  /**
   * Serializes the graph to a file in Cobra-compatible format.
   *
   * <p>The output format is:
   * <pre>
   * &lt;numNodes&gt;
   * e:&lt;srcId&gt;,&lt;dstId&gt;,&lt;edgeType&gt;
   * e:&lt;srcId&gt;,&lt;dstId&gt;,&lt;edgeType&gt;
   * ...
   * </pre>
   *
   * <p><b>Format Details:</b>
   * <ul>
   *   <li>First line contains the total number of nodes</li>
   *   <li>Subsequent lines represent edges, prefixed with "e:"</li>
   *   <li>Each edge is encoded as a comma-separated triple: (source, destination, type)</li>
   *   <li>Node IDs are encoded as integers via {@link #encodeNode(GraphNodeId)}</li>
   *   <li>Edge types are serialized using their enum name (e.g., "WR", "WW", "SO")</li>
   *   <li>If multiple edges exist between the same node pair, only the first is serialized</li>
   * </ul>
   *
   * <p><b>Append Mode:</b> The file is opened in append mode, allowing incremental graph
   * serialization across multiple invocations.
   *
   * @param filePath destination file path for graph serialization; must be writable
   */
  @Override
  public void dump(String filePath) {
    BufferedWriter out = null;

    try {
      FileWriter fstream = new FileWriter(filePath, true); // true tells to append data.
      out = new BufferedWriter(fstream);

      out.write(String.format("%d\n", nodes.size()));
      for (var u : adjList.keySet()) {
        Integer encodedU = encodeNode(u);

        for (var v : adjList.get(u).keySet()) {
          Integer encodedV = encodeNode(v);

          List<Pair<EdgeType, Key>> edges = new ArrayList<>(this.adjList.get(u).get(v));
          var firstEdge = edges.get(0);
          out.write(String.format("e:%d,%d,%s\n", encodedU, encodedV, firstEdge.getLeft().name()));
        }
      }

      out.close();
    } catch (IOException e) {
      System.err.println("Error: " + e.getMessage());
    }
  }
}
