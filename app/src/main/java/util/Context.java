package util;

import common.Key;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;


public class Context {
  public Class cls;
  private Map<Pair<Key, Integer>, Set<Integer>> readFrom;

  public Context(boolean debug) {
    this.readFrom = MapFactory.getEmptyMap(debug);
  }
}
