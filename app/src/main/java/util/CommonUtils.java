package util;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommonUtils {
  public static <K, V, P> Set<P> getIn(Map<K, Map<V, Set<P>>> extMap,
                                   K k, V v) {
    if (extMap.containsKey(k) && extMap.get(k).containsKey(v)) {
      return extMap.get(k).get(v);
    } else {
      return new HashSet<>();
    }
  }

  public static int bi(int txnId) {
    if (txnId == 0) {
      return 0;
    } else {
      return 2 * txnId - 1;
    }
  }

  public static int ci(int txnId) {
    if (txnId == 0) {
      return 0;
    }
    return 2 * txnId;
  }

  public static int bcId2TxnId(int bcId) {
    if (bcId == 0) {
      return 0;
    } else if (bcId % 2 == 1) {
      return (bcId + 1) / 2;
    } else {
      return bcId / 2;
    }
  }

  public static <T> String joinList(List<T> values, String separator) {
    if (values.isEmpty()) {
      return "";
    }
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(values.get(0));
    for (int i = 1; i < values.size(); i++) {
      stringBuilder.append(separator).append(values.get(i));
    }
    return stringBuilder.toString();
  }

  public static <T> Set<T> list2Set(List<T> list) {
    return new LinkedHashSet<>(list);
  }
}
