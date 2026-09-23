package history.op;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;

/**
 * RANGE: range queries over keys.
 */
public enum KvOpType {
  PUT, DELETE, READ, RANGE, ITER, BEGIN, COMMIT, ABORT;

  @Getter
  private static final Set<KvOpType> currentWriteTypes = new HashSet<>(List.of(PUT, DELETE));
  @Getter
  private static final  Set<KvOpType> allWriteTypes = new HashSet<>(List.of(PUT, DELETE));
  @Getter
  private static final  Set<KvOpType> currentReadTypes = new HashSet<>(List.of(READ, RANGE));
  /**
   * Unary operations: those that take only one key as input, unlike RANGE/ITER.
   */
  @Getter
  private static final  Set<KvOpType> unaryTypes = new HashSet<>(List.of(PUT, DELETE, READ));
  @Getter
  private static final Set<KvOpType> binaryTypes = new HashSet<>(List.of(RANGE, ITER));
}
