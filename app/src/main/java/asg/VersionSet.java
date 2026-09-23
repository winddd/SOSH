package asg;

import common.Key;

import java.util.HashMap;
import java.util.Map;

/**
 * only works for white-box setting.
 */
public class VersionSet {
  private Map<Key, Key> versionSet;

  public VersionSet() {
    versionSet = new HashMap<>();
  }

  public VersionSet(Map<Key, Key> versionSet) {
    this.versionSet = versionSet;
  }

  public Key getVersion(Key key) {
    return versionSet.getOrDefault(key, Key.getNullKey());
  }

  public boolean contains(Key key) {
    return versionSet.containsKey(key);
  }
}
