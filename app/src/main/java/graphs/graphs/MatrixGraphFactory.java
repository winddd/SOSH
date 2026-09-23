package graphs.graphs;

import org.nd4j.linalg.api.buffer.DataType;
import util.Config;
import util.enumtypes.MODE;

/**
 * Factory for creating matrix-based graph representations optimized for different backends.
 *
 * <p>Matrix graphs represent transaction dependencies using adjacency matrices rather than
 * traditional edge lists. This representation enables efficient batch operations and hardware
 * acceleration for certain verification algorithms.</p>
 *
 * <h2>Matrix Graph Implementations</h2>
 * <ul>
 *   <li><b>BitmapMatrixGraph</b>: Default implementation using RoaringBitmap for memory-efficient
 *       sparse matrix storage. Optimized for BFS-based reachability queries.</li>
 *   <li><b>NdArrayMatrixGraph</b>: GPU-accelerated implementation using ND4J/JavaCPP. Stores
 *       matrices in FLOAT16 format for GPU computation. Used when hardware acceleration is
 *       enabled.</li>
 *   <li><b>ArrayMatrixGraph</b>: Simple array-based implementation for small graphs or when
 *       partitioning is disabled.</li>
 * </ul>
 *
 * <h2>Selection Criteria</h2>
 * <p>The factory selects implementations based on:</p>
 * <ul>
 *   <li><b>BFS Flag</b>: When enabled, uses bitmap-based representation optimized for breadth-first
 *       search operations</li>
 *   <li><b>Partition Flag</b>: Determines whether to use GPU acceleration (false) or array-based
 *       storage (true)</li>
 *   <li><b>Hardware Availability</b>: GPU-based graphs require CUDA-capable hardware and ND4J natives</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * NodeIndexer indexer = new NodeIndexer(numTxns);
 * MatrixGraph graph = MatrixGraphFactory.getMatrixGraph(
 *     indexer,
 *     true,  // use BFS optimization
 *     false, // enable GPU if available
 *     cfg.debug,
 *     cfg.RUNMODE,
 *     cfg
 * );
 * graph.addEdge(txn1, txn2, EdgeType.WR);
 * }</pre>
 *
 * @see MatrixGraph
 * @see BitmapMatrixGraph
 * @see NdArrayMatrixGraph
 * @see ArrayMatrixGraph
 * @since 1.0
 */
public class MatrixGraphFactory {
  /**
   * Creates a matrix graph implementation based on optimization flags.
   *
   * <p>The factory analyzes the configuration to select the most appropriate matrix
   * representation for the verification workload.</p>
   *
   * @param indexer the node indexer mapping transaction IDs to matrix indices
   * @param useBFS whether to optimize for BFS-based reachability queries
   * @param partition whether to partition the graph (disables GPU acceleration)
   * @param debug whether to use deterministic data structures for debugging
   * @param mode the verification mode (may influence matrix layout)
   * @param cfg the global configuration object
   * @return a matrix graph implementation optimized for the specified parameters
   * @see NodeIndexer
   * @see MODE
   */
  public static MatrixGraph getMatrixGraph(NodeIndexer indexer, boolean useBFS,
                                           boolean partition, boolean debug, MODE mode,
                                           Config cfg) {
    MatrixGraph matrix;
    if (useBFS) {
      matrix = new BitmapMatrixGraph(indexer, debug, mode, cfg);
    } else {
      if (!partition) {
        // on GPU
        matrix = new NdArrayMatrixGraph(indexer, DataType.FLOAT16);
      } else {
        matrix = new ArrayMatrixGraph(indexer);
      }
    }

    return matrix;
  }
}
