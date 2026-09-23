package util;

import java.util.*;

/**
 * Factory for creating collection instances with configurable determinism.
 *
 * <p>This utility factory provides a centralized way to create maps and sets with
 * behavior that varies based on debug mode. In debug mode, deterministic collections
 * (LinkedHashMap, LinkedHashSet) are used to ensure reproducible iteration order,
 * which is critical for debugging and testing. In production mode, standard collections
 * (HashMap, HashSet) are used for optimal performance.</p>
 *
 * <h2>Determinism vs Performance</h2>
 * <ul>
 *   <li><b>Debug Mode</b>: Uses {@link LinkedHashMap} and {@link LinkedHashSet}, which
 *       maintain insertion order. This ensures that iterating over the same data always
 *       produces the same sequence, making debugging output consistent across runs.</li>
 *   <li><b>Production Mode</b>: Uses {@link HashMap} and {@link HashSet}, which offer
 *       better average-case performance but non-deterministic iteration order.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * boolean debug = cfg.debug;
 * Map<Integer, Transaction> txnMap = MapFactory.getEmptyMap(debug);
 * Set<Integer> visitedNodes = MapFactory.getEmptySet(debug);
 *
 * // In debug mode, iteration order matches insertion order
 * for (Transaction txn : txnMap.values()) {
 *   // Always processes in the same order when debug=true
 * }
 * }</pre>
 *
 * <h2>Design Rationale</h2>
 * <p>Centralizing collection creation through a factory allows the codebase to easily
 * switch between deterministic and non-deterministic data structures without modifying
 * call sites. This is particularly valuable for:</p>
 * <ul>
 *   <li>Reproducing bugs in verification logic</li>
 *   <li>Generating consistent test outputs</li>
 *   <li>Performance profiling with controlled conditions</li>
 * </ul>
 *
 * @see HashMap
 * @see LinkedHashMap
 * @see HashSet
 * @see LinkedHashSet
 * @since 1.0
 */
public class MapFactory {
  /**
   * Creates an empty map with determinism controlled by the debug flag.
   *
   * @param <K> the type of keys maintained by the map
   * @param <V> the type of mapped values
   * @param debug if true, returns a {@link LinkedHashMap} with insertion-order iteration;
   *              if false, returns a {@link HashMap} with better performance
   * @return an empty map instance
   */
  public static <K,V> Map<K, V> getEmptyMap(boolean debug){
    return debug ? new LinkedHashMap<>(): new HashMap<>();
  }

  /**
   * Creates an empty set with determinism controlled by the debug flag.
   *
   * @param <T> the type of elements maintained by the set
   * @param debug if true, returns a {@link LinkedHashSet} with insertion-order iteration;
   *              if false, returns a {@link HashSet} with better performance
   * @return an empty set instance
   */
  public static <T> Set<T> getEmptySet(boolean debug) {
    return debug ? new LinkedHashSet<>(): new HashSet<>();
  }
}
