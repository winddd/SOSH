package asg;

import java.util.*;

import asg.build.AccessMetadataCollector;
import common.Key;
import graphs.DirectDep;
import graphs.edges.TypeEdge;
import history.KVHistory;
import history.op.DeleteOp;
import history.op.OpUtil;
import lombok.Getter;
import org.jgrapht.alg.util.Triple;
import org.nd4j.linalg.api.ops.custom.Tri;
import util.Config;
import util.enumtypes.ISOLATION_LEVEL;

/**
 * Abstract Syntax Graph - Central data structure representing transaction
 * history as a graph.
 *
 * <p>
 * ASG (Abstract Syntax Graph) serves as the core abstraction for analyzing
 * transaction histories
 * in Boomslang. It transforms a linear transaction log ({@link KVHistory}) into
 * a rich graph structure
 * that captures dependencies, read/write relationships, and metadata needed for
 * isolation level verification.
 *
 * <p>
 * <b>Architecture:</b>
 * <ul>
 * <li><b>Metadata Layer:</b> {@link ReadMetadata} and {@link WriteMetadata}
 * provide indexed access
 * to read and write operations by key, value, transaction, and operation
 * type.</li>
 * <li><b>Dependency Layer:</b> {@link DependencyStore} manages transaction
 * dependencies including
 * typed edges ({@link TypeEdge}) and direct dependencies
 * ({@link DirectDep}).</li>
 * <li><b>Derived Structures:</b> {@link VersionOrder} is
 * built eagerly after dependency population to support compilation and
 * analysis. {@link ReadIndex} is now built on-demand by consumers (v1 compilers, GenWriteDepModule).</li>
 * </ul>
 *
 * <p>
 * <b>Lifecycle:</b>
 * <ol>
 * <li>Construct: {@code new ASG(history, config)}</li>
 * <li>Build metadata: {@link #preBuild()} - populates readMetadata,
 * writeMetadata, initialState</li>
 * <li>Build dependencies: External builders (via
 * {@link buildASG.ASGConstructor}) populate edges and DirectDeps</li>
 * <li>Build indexes: {@link #buildVersionOrder()}
 * - called by ASGConstructor after dependency population</li>
 * <li>Consume: Downstream compilers and analyzers query the ASG via
 * getters</li>
 * </ol>
 *
 * <p>
 * <b>Thread Safety:</b> ASG is NOT thread-safe. It should be built sequentially
 * and then
 * consumed read-only by downstream code.
 *
 * @see buildASG.ASGConstructor for the standard construction workflow
 * @see ReadMetadata for read operation metadata
 * @see WriteMetadata for write operation metadata
 * @see DependencyStore for dependency management
 */
public class ASG {
  @Getter
  private final KVHistory history;
  private final ISOLATION_LEVEL isolationLevel;
  private final Config cfg;
  // core:
  @Getter
  private final DependencyStore dependencyStore;
  @Getter
  private Hints hints;
  @Getter
  private int numTxns;
  // `k2WriteTxns` includes both internal and external
  private Set<Key> keys;
  @Getter
  private Map<Key, Key> initialState; // required
  // Overall extWrites, intermtWrites, extReads
  @Getter
  private ReadMetadata readMetadata;
  @Getter
  private VersionOrder versionOrder;
  @Getter
  private WriteMetadata writeMetadata;

  public ASG(KVHistory history, Config cfg) {
    this.history = history;
    this.cfg = cfg;
    this.isolationLevel = cfg.RUNMODE.getIsolationLevel();
    this.dependencyStore = new DependencyStore(cfg);
    this.readMetadata = ReadMetadata.empty();
    this.hints = new Hints();
  }

  /**
   * Builds auxiliary data structures from the transaction history.
   *
   * <p>
   * This method must be called immediately after construction and before any
   * other operations.
   * It initializes:
   * <ul>
   * <li>Transaction count ({@code numTxns})</li>
   * <li>Key set ({@code keys})</li>
   * <li>Initial state with null-key entries for non-existent keys</li>
   * <li>Read metadata via {@link asg.build.AccessMetadataCollector}</li>
   * <li>Write metadata via {@link WriteMetadata#collect}</li>
   * </ul>
   *
   * <p>
   * <b>Side Effects:</b> May add {@link DeleteOp} operations to the initial
   * transaction (T0)
   * for keys that don't exist in the initial state, depending on configuration
   * flags
   * ({@code POLYSI_COMPATIBLE_MODE}, {@code DISABLE_INITIALTXN}).
   *
   * @throws AssertionError if T0 operations don't match initial state size
   */
  public void preBuild() {
    this.numTxns = history.length();
    // this.constraints = new ArrayList<>();
    this.keys = history.allKeys();
    // Assume that T0 creates an initial version for all keys in K.
    var T0 = history.getKthTxn(0);
    assert (T0.getMops().size() == history.getInitialState().size());
    // NOTE: create initial writes for those items that don't exist in initialState.
    if (!cfg.POLYSI_COMPATIBLE_MODE) {
      // some keys doesn't actually exist in the initial state, but they may also be
      // read,
      // we create a dummy put operation for them, they are only allowed to read a
      // null value.
      if (!cfg.DISABLE_INITIALTXN) { // TODO: consider remove this if check
        var ctx = new HashMap<String, Object>();
        ctx.put(OpUtil.THREADID_TAG, OpUtil.INIT_SESSION_ID);
        ctx.put(OpUtil.TXNINDEX_TAG, OpUtil.INIT_TXN_INDEX);

        for (Key key : keys) {
          if (!history.getInitialState().containsKey(key)) {
            // also update initialState
            history.getInitialState().put(key, Key.getNullKey());
            // create an initial write
            // T0.addOperations(new PutOp(key, Key.getNullKey()));
            T0.addOperations(new DeleteOp(key, ctx));
          }
        }
      }
    }

    this.initialState = Collections.unmodifiableMap(new HashMap<>(history.getInitialState()));

    var accessMetadataResult = new AccessMetadataCollector(cfg).collect(history);
    this.readMetadata = accessMetadataResult.reads();

    // TODO: ext_read_fn doesn't process the non-returned items of range/iter, if
    // you want to let it
    // process that, you need to remove the separate processing of it below.

    // extWrites, extInserts, extUpdates, extPuts, extDels, extWritesByCategory
    // allInserts, allUpdates, allPuts, allDels, allWritesByCategory
    this.writeMetadata = WriteMetadata.collect(history, cfg);
  }

  public Config getConfig() {
    return cfg;
  }

  /**
   * Allows tests to override the derived txn count when constructing stub ASGs.
   */
  public void overrideNumTxnsForTesting(int numTxns) {
    this.numTxns = numTxns;
  }

  public void resetHints() {
    this.hints = new Hints();
  }

  public Set<Key> getKeys() {
    return keys == null ? Set.of() : Collections.unmodifiableSet(keys);
  }

  /**
   * Builds the VersionOrder by inferring write chains from RMW
   * (Read-Modify-Write) patterns.
   *
   * <p>
   * This method constructs version order chains for keys by analyzing RMW
   * transactions.
   * When a transaction reads and writes the same key, and the read value uniquely
   * identifies
   * a single preceding write, we can infer that those two writes are consecutive
   * in the
   * version order for that key.
   *
   * <p>
   * This method should be called by {@link buildASG.ASGConstructor} after all
   * GraphBuilders have populated DirectDeps.
   *
   * <p>
   * <b>Algorithm:</b> For each DirectDep representing an RMW pattern with a
   * unique
   * candidate write, combine the write chains of the reading and writing
   * transactions.
   *
   * <p>
   * <b>Isolation Level Dependency:</b> Version order inference from RMW is only
   * valid
   * under Serializable (SER) and Snapshot Isolation (SI).
   *
   * <p>
   * The resulting VersionOrder is cached and reused on subsequent calls to
   * {@link #getVersionOrder()}.
   */
  public void buildVersionOrder() {
    var directDeps = dependencyStore.directDeps();
    versionOrder = new VersionOrder(this);

    // we can only use RMW to infer version order under SER or SI.
    if (isolationLevel.canInferVersionOrderFromRMW()) {
      // A txn can have multiple reads that will combine the same txns multiple times. We use a hash set to avoid it.
      Set<Triple<Integer, Integer, Key>> processed = new HashSet<>();
      for (var directDep : directDeps) {
        // if a RMW txn is found and unique value, a pair of consecutive writes are
        // determined.
        List<WriteEvent> candidates = directDep.getCandidateWriteEvents();
        if (directDep.isRMW && candidates.size() == 1) {
          var uniqueCandidate = candidates.get(0);

          if (uniqueCandidate.getTxnId() != directDep.txnId) {
            var txn1 = history.getKthTxn(candidates.get(0).getTxnId());
            var txn2 = history.getKthTxn(directDep.txnId);

            var triple = Triple.of(txn1.getTxnId(), txn2.getTxnId(), directDep.key);
            if (!processed.contains(triple)) {
              versionOrder.combineTwoChains(directDep.key, txn1, txn2);
              processed.add(triple);
            }
          }
        }
      }
    }
  }
}
