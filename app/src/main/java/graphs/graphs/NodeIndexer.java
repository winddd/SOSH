package graphs.graphs;

import graphs.nodes.GraphNodeId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maintains a stable mapping between {@link GraphNodeId} instances and dense matrix indices.
 */
public final class NodeIndexer {
  private final List<GraphNodeId> indexToNode;
  private final Map<GraphNodeId, Integer> nodeToIndex;

  private NodeIndexer(List<GraphNodeId> orderedNodes) {
    this.indexToNode = Collections.unmodifiableList(orderedNodes);
    Map<GraphNodeId, Integer> mapping = new HashMap<>(orderedNodes.size());
    for (int i = 0; i < orderedNodes.size(); i++) {
      mapping.put(orderedNodes.get(i), i);
    }
    this.nodeToIndex = Collections.unmodifiableMap(mapping);
  }

  public static NodeIndexer from(Collection<GraphNodeId> nodes) {
    Objects.requireNonNull(nodes, "nodes");
    List<GraphNodeId> ordered = new ArrayList<>(nodes);
    ordered.sort(Comparator
        .comparing(GraphNodeId::kind)
        .thenComparing(GraphNodeId::txnId)
        .thenComparing(GraphNodeId::opIndex)
        .thenComparing(node -> node.actionType().map(Enum::ordinal).orElse(-1)));
    return new NodeIndexer(ordered);
  }

  public int size() {
    return indexToNode.size();
  }

  public GraphNodeId nodeAt(int index) {
    return indexToNode.get(index);
  }

  public int requireIndex(GraphNodeId nodeId) {
    Integer idx = nodeToIndex.get(nodeId);
    if (idx == null) {
      throw new IllegalArgumentException("Node not indexed: " + nodeId);
    }
    return idx;
  }

  public boolean contains(GraphNodeId nodeId) {
    return nodeToIndex.containsKey(nodeId);
  }

  public List<GraphNodeId> nodes() {
    return indexToNode;
  }

  public boolean isTxnOnly() {
    return indexToNode.stream().allMatch(GraphNodeId::isTxn);
  }
}
