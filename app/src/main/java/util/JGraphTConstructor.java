package util;

import graphs.graphs.InCompleteGraph;
import util.enumtypes.ISOLATION_LEVEL;
import util.enumtypes.MODE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class JGraphTConstructor {
  private int cyclesFound = 0;
  private int maxCycles = 1;

  public void cycleDetectionInKnownGraph(InCompleteGraph graph, MODE mode) {
    cycleDetectionInKnownGraph(graph, mode, 1);
  }

  public void cycleDetectionInKnownGraph(InCompleteGraph graph, MODE mode, int numCycles) {
    this.cyclesFound = 0;
    this.maxCycles = numCycles;
    Set<Integer> visited = new HashSet<>();
    List<Integer> stack = new ArrayList<>(); // Tracks nodes in the current DFS path

    for (Integer node : graph.getNodes()) {
      if (cyclesFound >= maxCycles) break; // Stop if we've found enough cycles
      if (!visited.contains(node) && dfs(graph, node, visited, stack)) {
        int lastIndex = stack.indexOf(stack.get(stack.size()-1));
        for (int i = 0; i < lastIndex; i ++) {
          stack.remove(0);
        }

        System.out.println("Cycle #" + cyclesFound + " found!");
        System.out.println("Node IDs: " + stack);

        if (mode.getIsolationLevel() == ISOLATION_LEVEL.SNAPSHOT_ISOLATION) {
          List<Integer> txnIds = stack.stream().
              map(bcId -> CommonUtils.bcId2TxnId(bcId))
              .collect(Collectors.toList());
          System.out.println("Txn IDs: " + txnIds);
        }

        printACycle(graph, stack, mode);

        // Reset for finding next cycle
        stack.clear();
      }
    }

    if (cyclesFound == 0) {
      System.out.println("No cycle exists in the graph.");
    } else {
      System.out.println("Total cycles found: " + cyclesFound);
    }
  }

  private void printACycle(InCompleteGraph graph, List<Integer> stack, MODE mode) {
    StringBuilder s = new StringBuilder();
    int i = 0;
    for (; i < stack.size()-1; i ++) {
      var u = stack.get(i);
      var v = stack.get(i+1);

      if (mode.getIsolationLevel() == ISOLATION_LEVEL.SNAPSHOT_ISOLATION) {
        s.append(CommonUtils.bcId2TxnId(u));
      } else {
        s.append(u);
      }
      s.append(" -");

      //
      List<String> types = new ArrayList<>();
      // Find GraphNodeIds for these encoded integers
      var uNodeId = graph.getNodeIds().stream().filter(id -> id.txnId() == u).findFirst().orElse(null);
      var vNodeId = graph.getNodeIds().stream().filter(id -> id.txnId() == v).findFirst().orElse(null);
      if (uNodeId != null && vNodeId != null) {
        for (var p : graph.getAdjList().get(uNodeId).get(vNodeId)) {
          var edgeType = p.getLeft();
          var key = p.getRight();
          types.add(String.format("%s(%s)", edgeType, key));
        }
      }
      String typeStr = String.join("/", types);
      s.append(typeStr);
      s.append("-> ");
    }

    if (mode.getIsolationLevel() == ISOLATION_LEVEL.SNAPSHOT_ISOLATION) {
      s.append(CommonUtils.bcId2TxnId(stack.get(i)));
    } else {
      s.append(stack.get(i));
    }
    System.out.println(s);
  }

  private boolean dfs(InCompleteGraph graph, int node, Set<Integer> visited, List<Integer> stack) {
    if (stack.contains(node)) {
      stack.add(node);
      cyclesFound++;
      return true; // Cycle detected
    }

    if (visited.contains(node)) {
      return false; // Node already fully processed
    }

    visited.add(node);
    stack.add(node);

    // Find GraphNodeId for this encoded integer
    var nodeId = graph.getNodeIds().stream().filter(id -> id.txnId() == node).findFirst().orElse(null);
    if (nodeId != null) {
      for (var neighborNodeId : graph.getAdjList().get(nodeId).keySet()) {
        var neighborEncoded = neighborNodeId.txnId();
        // Skip self-loops
        if (neighborEncoded == node) {
          continue;
        }
        if (dfs(graph, neighborEncoded, visited, stack)) {
          return true;
        }
      }
    }

    stack.remove(stack.indexOf(node)); // Remove from stack after processing
    return false;
  }
}
