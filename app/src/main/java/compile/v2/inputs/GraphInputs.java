package compile.v2.inputs;

import asg.ASG;
import asg.ReadMetadata;
import asg.WriteMetadata;
import asg.VersionOrder;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import graphs.DirectDep;
import graphs.edges.TypeEdge;
import history.KVHistory;
import util.Config;

public record GraphInputs(ASG asg,
    int numTxns,
    KVHistory history,
    WriteMetadata writes,
    ReadMetadata reads,
    Collection<DirectDep> directDeps,
    Collection<TypeEdge> edges,
    VersionOrder versionOrder,
    Map<Integer, List<Integer>> sessionOrder,
    Config config) {

  public static GraphInputs from(ASG asg) {
    KVHistory history = asg.getHistory();
    WriteMetadata writes = asg.getWriteMetadata();
    Collection<DirectDep> deps = asg.getDependencyStore().directDeps();
    Collection<TypeEdge> edges = asg.getDependencyStore().edges();
    VersionOrder versionOrder = asg.getVersionOrder();
    ReadMetadata reads = asg.getReadMetadata();
    Map<Integer, List<Integer>> sessionOrder = new HashMap<>();
    Config config = asg.getConfig();
    return new GraphInputs(asg, asg.getNumTxns(), history, writes, reads, deps, edges, versionOrder,
        sessionOrder, config);
  }

  public boolean isValidTxnId(int txnId) {
    return txnId >= 0 && txnId < numTxns;
  }

  public void requireValidTxnId(int txnId) {
    if (!isValidTxnId(txnId)) {
      throw new IllegalArgumentException("Txn id out of range: " + txnId);
    }
  }

  /**
   * Returns all DirectDeps for a specific transaction.
   *
   * @param txnId the transaction ID to filter by
   * @return a list of DirectDeps where the reading transaction matches txnId
   */
  public List<DirectDep> directDepsForTxn(int txnId) {
    return directDeps.stream()
        .filter(dep -> dep.txnId.equals(txnId))
        .toList();
  }
}
