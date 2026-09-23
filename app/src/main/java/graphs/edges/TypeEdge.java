package graphs.edges;

import common.Key;
import graphs.nodes.GraphNodeId;
import graphs.nodes.NodeKind;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import util.CommonUtils;

import java.io.Serializable;
import java.util.Objects;

/**
 * Typed dependency edge representing a specific kind of relationship between transactions or operations.
 *
 * <p>TypeEdges are the fundamental building blocks of constraint graphs in Boomslang. Each edge
 * captures a dependency between two graph nodes (transactions or individual operations) with a
 * specific semantic type (write-read, write-write, etc.) and an associated key.</p>
 *
 * <h2>Edge Types</h2>
 * <p>The {@link EdgeType} determines the semantics of the dependency:</p>
 * <ul>
 *   <li><b>WR</b>: Write-Read dependency (anti-dependency) - target reads from source's write</li>
 *   <li><b>WW</b>: Write-Write dependency (output dependency) - both write the same key</li>
 *   <li><b>RW</b>: Read-Write dependency (true dependency) - target writes after source reads</li>
 *   <li><b>CB</b>: Commit-Before ordering constraint</li>
 *   <li><b>PWR</b>: Predicate Write-Read for range queries</li>
 *   <li><b>PRW</b>: Predicate Read-Write for range queries</li>
 * </ul>
 *
 * <h2>Node Granularity</h2>
 * <p>TypeEdges can connect nodes at two granularities:</p>
 * <ul>
 *   <li><b>Transaction-level</b>: Edges between entire transactions (e.g., T1 → T2)</li>
 *   <li><b>Operation-level</b>: Edges between specific operations within transactions
 *       (e.g., T1:op3 → T2:op5), used for fine-grained PL-2L checking</li>
 * </ul>
 *
 * <h2>Begin-Commit (BC) Edges</h2>
 * <p>Some isolation levels require reasoning about when transactions begin and commit.
 * BC edges map operation-level dependencies to begin/commit events using special encoding:</p>
 * <ul>
 *   <li>RW/PRW edges: connect begin(source) to commit(target)</li>
 *   <li>WW/WR/PWR/CB edges: connect commit(source) to begin(target)</li>
 * </ul>
 *
 * <h2>Encoding and Comparison</h2>
 * <p>Internally, nodes are encoded as integers for efficient graph operations. Transaction-level
 * nodes use positive integers (txnId), while operation-level nodes use negative encodings to
 * avoid collisions. The class extends {@link Edge} which provides the numeric u/v fields.</p>
 *
 * <h2>Equality and Hashing</h2>
 * <p>Two TypeEdges are equal if they have the same edge type and key, regardless of source/target
 * nodes. This allows checking for edge existence independent of specific node pairs.</p>
 *
 * <h2>Priority</h2>
 * <p>The priority field supports weighted search optimizations where certain edges are explored
 * preferentially during cycle detection (see {@link optimizations.WeightGuidedOpt}).</p>
 *
 * @see Edge
 * @see EdgeType
 * @see GraphNodeId
 * @see graphs.graphs.InCompleteGraph
 * @since 1.0
 */
@EqualsAndHashCode(callSuper = true, of = { "edgeType", "key" })
public class TypeEdge extends Edge implements Serializable {
  @Getter
  private final GraphNodeId sourceId;
  @Getter
  private final GraphNodeId targetId;
  public EdgeType edgeType;
  public Key key;
  private String template = "[%s -%s(%s)-> %s]";
  private String template2 = "[%s -%s-> %s]";
  private boolean isBCEdge = false;
  @Setter
  @Getter
  private int priority = 0;

//  @Deprecated
//  public TypeEdge(int u, int v, EdgeType edgeType, Key key) {
//    this(GraphNodeId.txn(u), GraphNodeId.txn(v), edgeType, key, false);
//  }
//
//  @Deprecated
//  public TypeEdge(int u, int v, EdgeType edgeType, Key key, boolean isBCEdge) {
//    this(GraphNodeId.txn(u), GraphNodeId.txn(v), edgeType, key, isBCEdge);
//  }

  /**
   * Creates a transaction-level or operation-level typed edge.
   *
   * <p>This is the primary constructor for creating dependency edges. The granularity
   * (transaction vs operation) is determined by the {@link NodeKind} of the source and target IDs.</p>
   *
   * @param sourceId the source node identifier (transaction or operation)
   * @param targetId the target node identifier (transaction or operation)
   * @param edgeType the type of dependency relationship
   * @param key the key associated with this dependency (may be wildcard for certain edge types)
   * @throws NullPointerException if any parameter is null
   */
  public TypeEdge(GraphNodeId sourceId, GraphNodeId targetId, EdgeType edgeType, Key key) {
    this(sourceId, targetId, edgeType, key, false);
  }

  /**
   * Creates a typed edge with optional begin-commit encoding.
   *
   * <p>When isBCEdge is true, this constructor marks the edge as already transformed to
   * begin-commit form. This is used internally by {@link #toBCEdge()}.</p>
   *
   * @param sourceId the source node identifier
   * @param targetId the target node identifier
   * @param edgeType the type of dependency relationship
   * @param key the key associated with this dependency
   * @param isBCEdge whether this edge is in begin-commit form
   * @throws NullPointerException if sourceId, targetId, or edgeType is null
   */
  public TypeEdge(GraphNodeId sourceId, GraphNodeId targetId, EdgeType edgeType, Key key, boolean isBCEdge) {
    super(encodeNode(Objects.requireNonNull(sourceId, "sourceId")),
        encodeNode(Objects.requireNonNull(targetId, "targetId")));
    this.edgeType = Objects.requireNonNull(edgeType, "edgeType");
    this.key = key;
    this.isBCEdge = isBCEdge;
    this.sourceId = sourceId;
    this.targetId = targetId;
  }

  // public int hashCode() { // u | v
  // return u << 16 + v;
  // }

  /**
   * Encodes a GraphNodeId into an integer for use in the parent Edge class.
   *
   * <p>Transaction-level nodes map directly to their transaction ID (positive integers).
   * Operation-level nodes are encoded as negative integers using the formula:
   * {@code -1 - (txnId * 100000 + opIndex)} to avoid collisions with transaction IDs.</p>
   *
   * @param nodeId the node identifier to encode
   * @return an integer encoding suitable for graph operations
   */
  private static int encodeNode(GraphNodeId nodeId) {
    if (nodeId.kind() == NodeKind.TXN) {
      return nodeId.txnId();
    }
    // Encode operation-level nodes as negative integers to avoid collision with txn
    // ids.
    int base = nodeId.txnId() * 100000 + Math.max(nodeId.opIndex(), 0);
    return -1 - base;
  }

  // public boolean equals(Object o) {
  // if (this == o) {
  // return true;
  // }
  // if (o == null || this.getClass() != o.getClass()) {
  // return false;
  // }
  // TypeEdge e = (TypeEdge) o;
  // return this.u == e.u && this.v == e.v && this.edgeType == e.edgeType;
  // }

  /**
   * Formats a GraphNodeId for human-readable output.
   *
   * <p>Transaction nodes are formatted as just the txn ID. Operation nodes are formatted
   * as "txnId:opIndex(actionType)".</p>
   *
   * @param nodeId the node to format
   * @return a human-readable string representation
   */
  private static String formatNode(GraphNodeId nodeId) {
    if (nodeId.kind() == NodeKind.TXN) {
      return Integer.toString(nodeId.txnId());
    }
    String type = nodeId.actionType().map(Enum::name).orElse("?");
    return nodeId.txnId() + ":" + nodeId.opIndex() + "(" + type + ")";
  }

  /**
   * Returns a human-readable representation of this edge.
   *
   * @return a string in the format "[source -edgeType(key)-> target]"
   */
  public String toString() {
    return String.format(template, formatNode(sourceId), edgeType.name(), key, formatNode(targetId));
  }

  /**
   * Compares this edge to another based on source node first, then target node.
   *
   * <p>Used for deterministic ordering of edges in sorted collections.</p>
   *
   * @param o the edge to compare to
   * @return negative if this < o, positive if this > o, zero if equal
   */
  public int compareTo(TypeEdge o) {
    if (this.u > o.u) {
      // if current object is greater --> return 1
      return 1;
    } else if (this.u < o.u) {
      // if current object is greater --> return -1
      return -1;
    } else {
      // if current object is equal to o --> return 0
      return (int) (this.v - o.v);
    }
  }

  /**
   * Transforms this edge into begin-commit form for strict serializability checking.
   *
   * <p>Strict serializability requires that transaction execution order matches real-time order.
   * Begin-commit edges model when transactions begin and commit, allowing verification of
   * real-time constraints.</p>
   *
   * <p>The transformation depends on edge type:</p>
   * <ul>
   *   <li>RW/PRW: begin(source) → commit(target)</li>
   *   <li>WW/WR/PWR/CB: commit(source) → begin(target)</li>
   * </ul>
   *
   * <p>If this edge is already in BC form, returns a copy unchanged.</p>
   *
   * @return a new TypeEdge in begin-commit form
   * @throws RuntimeException if the edge type cannot be converted to BC form
   * @see util.CommonUtils#bi(int)
   * @see util.CommonUtils#ci(int)
   */
  public TypeEdge toBCEdge() {
    if (isBCEdge) {
      return new TypeEdge(GraphNodeId.txn(u), GraphNodeId.txn(v), this.edgeType, this.key, true);
    } else {
      int u, v;
      switch (this.edgeType) {
        case RW, PRW -> {
          u = CommonUtils.bi(this.u);
          v = CommonUtils.ci(this.v);
        }
        case WW, WR, PWR, CB -> {
          u = CommonUtils.ci(this.u);
          v = CommonUtils.bi(this.v);

          // If this WR/PWR is a self loop, we also convert it into an edge from begin to commit.
          if ((this.edgeType == EdgeType.WR || this.edgeType == EdgeType.PWR) && this.u == this.v) {
            u = CommonUtils.bi(this.u);
            v = CommonUtils.ci(this.v);
          }
        }
        default -> {
          throw new RuntimeException();
        }
      }

      TypeEdge newEdge = new TypeEdge(GraphNodeId.txn(u), GraphNodeId.txn(v), this.edgeType, this.key, true);
      return newEdge;
    }
  }

  /**
   * Checks whether this edge is in begin-commit form.
   *
   * @return true if this edge connects begin/commit events rather than transactions
   */
  public boolean isBCEdge() {
    return isBCEdge;
  }

  // private static Collection<>

  // // public void addGeneralizedConstraint(GeneralizedConstraint con) {
  // // generalCons.add(this.buildBCGeneralCon(con));
  // // }

  // public static Collection<TypeEdge> buildBCEdges(Collection<TypeEdge> es) {
  // for (TypeEdge edge : es) {
  // TypeEdge bcTypeEdge = this.buildBCEdge(edge.u, edge.v, edge.edgeType);
  // edge.u = bcTypeEdge.u;
  // edge.v = bcTypeEdge.v;
  // edge.edgeType = bcTypeEdge.edgeType;
  // }

  // return es;
  // }

}
