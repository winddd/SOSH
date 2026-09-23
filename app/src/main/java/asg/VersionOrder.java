package asg;

import common.Key;
import history.KVTxn;
import history.op.KvOperation;
import util.exception.RejectException;

import java.util.*;

public class VersionOrder {
  /**
   * key => partial version order.
   */
  public Map<Key, List<List<KVTxn>>> k2PartialOrder;
  private ASG ASG;

  public VersionOrder(ASG ASG) {
    this.ASG = ASG;
    k2PartialOrder = new HashMap<>();

    Map<Key, Set<Integer>> k2ExtWrites = ASG.getWriteMetadata().k2ExtWriteTxnIds();
    for (var key : k2ExtWrites.keySet()) {
      for (var txnId : k2ExtWrites.get(key)) {
        var chain = new ArrayList<KVTxn>();
        chain.add(ASG.getHistory().getKthTxn(txnId));
        this.addChain(key, chain);
      }
    }
  }

  public void addChain(Key key, List<KVTxn> chain) {
    // debug
//    if (!(chain != null &&!chain.isEmpty())) {
//      throw new RuntimeException("");
//    }
    assert chain != null && !chain.isEmpty();
    if (!k2PartialOrder.containsKey(key)) {
      k2PartialOrder.put(key, new ArrayList<>());
    }
    k2PartialOrder.get(key).add(chain);
  }

  /**
   * combine consecutive writes
   * @param key
   * @param txn1
   * @param txn2
   */
  public void combineTwoChains(Key key, KVTxn txn1, KVTxn txn2) {
    var kChains = k2PartialOrder.get(key);
    var chain1 = findChainEndWith(kChains, txn1);
    var chain2 = findChainStartWith(kChains, txn2);
    kChains.remove(chain2);
    chain1.addAll(chain2);
  }

  private List<KVTxn> findChainEndWith(List<List<KVTxn>> kChains, KVTxn txn) {
    List<KVTxn> ret;
    try {
      ret = findChainWithKthTxn(kChains, txn, -1);
    } catch (RejectException e) {
      throw new RejectException("Cannot find a chain of txns that ends with txn T" + txn.getTxnId());
    }
    return ret;
  }

  private List<KVTxn> findChainStartWith(List<List<KVTxn>> kChains, KVTxn txn) {
    List<KVTxn> ret;
    try {
      ret = findChainWithKthTxn(kChains, txn, 0);
    } catch (RejectException e) {
      throw new RejectException("Cannot find a chain of txns that starts with txn T" + txn.getTxnId());
    }
    return ret;
  }

  /**
   * find the chain that has txn at kth position.
   * @return
   */
  private List<KVTxn> findChainWithKthTxn(List<List<KVTxn>> kChains, KVTxn txn, int k) {
    for (var chain : kChains) {
      var n = chain.size();
      if (chain.get((k + n) % n) == txn) {
        return chain;
      }
    }

    throw new RejectException("");
  }

  private List<KVTxn> findChainContainingTxn(List<List<KVTxn>> kChains, KVTxn txn) {
    for (var chain : kChains) {
      if (chain.contains(txn)) {
        return chain;
      }
    }

    return null;
  }

  public KVTxn getPreviousWrite(Key key, KVTxn txn) {
    var partialVO = getParitialOrder(key, txn);
    var idx = partialVO.indexOf(txn) - 1;
    return idx >= 0 ? partialVO.get(idx) : null;
  }

  public KVTxn getSubsequenceWrite(Key key, KVTxn txn) {
    var partialVO = getParitialOrder(key, txn);
    var idx = partialVO.indexOf(txn) + 1;
    return idx < partialVO.size() ? partialVO.get(idx) : null;
  }

  public KVTxn getSubsequentPut(Key key, KVTxn txn) {
    KVTxn nextTxn;
    while ((nextTxn = getSubsequenceWrite(key, txn)) != null) {
      if (ASG.getWriteMetadata().hasExternalPut(key, nextTxn.getTxnId())) {
        return nextTxn;
      }
      txn = nextTxn;
    }

    return null;
  }

  public KVTxn getSubsequentDelete(Key key, KVTxn txn) {
    KVTxn nextTxn;
    while ((nextTxn = getSubsequenceWrite(key, txn)) != null) {
      if (ASG.getWriteMetadata().hasExternalDelete(key, nextTxn.getTxnId())) {
        return nextTxn;
      }
      txn = nextTxn;
    }

    return null;
  }

  /**
   * get the partial order of `key` that contains `txn`.
   * @param key
   * @param txn
   * @return
   */
  public List<KVTxn> getParitialOrder(Key key, KVTxn txn) {
    for (var partialVO : k2PartialOrder.get(key)) {
      if (partialVO.contains(txn)) {
        return partialVO;
      }
    }
    return null;
  }

  public List<List<KVTxn>> getPartialOrder(Key key) {
    return k2PartialOrder.get(key);
  }

  public boolean succeeds(KvOperation mop1, KvOperation mop2) {
    assert mop1.getKey().equals(mop2.getKey());
    var versions = this.k2PartialOrder.get(mop1.getKey());
    var index1 = versions.indexOf(mop1);
    var index2 = versions.indexOf(mop2);
    assert index1 >= 0 && index2 >= 0 && index1 != index2;
    return index1 > index2;
  }

  /**
   * check if the version order is a total order (not a partial order) for all keys.
   * @return
   */
  public boolean hasCompleteVersionOrder() {
    for (var key: k2PartialOrder.keySet()) {
      var kChains = k2PartialOrder.get(key);
      if (kChains.size() > 1) {
        return false;
      }
    }

    return true;
  }
}
