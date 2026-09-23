package common;

import java.io.Serializable;
import java.util.*;

import lombok.EqualsAndHashCode;
import util.MapFactory;

//@EqualsAndHashCode(of = {"bytes"})
public class Key implements Comparable<Key>, Serializable {
  private static final long serialVersionUID = 652945488267757692L;
  private byte[] bytes;
  private byte[] cmpUnits;

  public Key(byte[] bytes) {
    this.bytes = bytes;
  }

  public static Key getMinKey() {
    return new Key(new byte[]{0});
  }

  // TODO: this is for get the maximum value, but what is the maximum value in tikv?
  // encodedColVals can be any str?
  public static Key getMaxKey() {
    return KeyUtils.maxKey;
  }

  public static Key getMaxKey(Collection<Key> keys) {
    assert !keys.isEmpty();

    List<Key> keyList = new ArrayList(keys);
    Key maxKey = keyList.get(0);

    for (var key : keys) {
      if (key.compareTo(maxKey) > 0) {
        maxKey = key;
      }
    }

    return maxKey;
  }

  public static Key getNullKey() {
    Key key = new Key(null);
    return key;
  }

  protected static Set<Key> subset(Set<Key> allKeys, Key key1, Key key2, boolean debug) {
    return subsetWithExclude(allKeys, key1, key2, MapFactory.getEmptySet(debug), debug);
  }

  protected static Set<Key> subsetWithExclude(Set<Key> allKeys, Key key1, Key key2,
                                              Set<Key> excluding, boolean debug) {
    assert key1.compareTo(key2) < 0;
    Set<Key> set1 = MapFactory.getEmptySet(debug);

    for (var key : allKeys) {
      if (key.inBetween(key1, key2) && !excluding.contains(key)) {
        set1.add(key);
      }
    }

    return set1;
  }

  public boolean inBetween(Key k1, Key k2, Key v, Key v1, Key v2) {
    if (v == null) { // wait: why v can be null?
      return false;
    }

    boolean ret1 = this.inBetween(k1, k2);
    boolean ret2 = v.inBetween(v1, v2);
    return ret1 & ret2;
  }

  /**
   *
   *
   * @param k1
   * @param k2
   * @return
   */
  public boolean inBetween(Key k1, Key k2) {
    if(this.isNull())
      return false;
    if ((k1 == null || k1.isNull()) && !(k2 == null || k2.isNull()))
      return this.compareTo(k2) < 0;
    else if ((k2 == null || k2.isNull()) && !(k1 == null || k1.isNull()))
      return k1.compareTo(this) <= 0;
    else
      return k1.compareTo(this) <= 0 && this.compareTo(k2) < 0;
  }

  private synchronized void computeCmpUnits() {
    if (this.cmpUnits != null)
      return;

    this.cmpUnits = flipMostSignificantBits(this.bytes);
  }

  @Override
  public int compareTo(Key o) {
    assert o != null;
    if (this.isNull() && !o.isNull())
      return -1;
    else if(this.isNull() && o.isNull())
      return 0;
    else if(!this.isNull() && o.isNull())
      return 1;

    assert !this.isNull() && !o.isNull();

    this.computeCmpUnits();
    o.computeCmpUnits();
    byte[] b1 = this.cmpUnits, b2 = o.cmpUnits;

    int n = Math.min(b1.length, b2.length);
    int i = 0;
    for (; i < n; i ++) {
      if (b1[i] < b2[i])
        return -1;
      else if (b2[i] < b1[i])
        return 1;
    }

    return b1.length - b2.length;
  }

  @Override
  public int hashCode(){
    return Arrays.hashCode(this.getBytes());
  }

  @Override
  public boolean equals(Object o) {
    if(!(o instanceof Key))
      return false;

    Key oKey = (Key) o;
    return Arrays.equals(this.getBytes(), oKey.getBytes());
  }

  public String toString() {
    if (this.equals(Key.getNullKey())){
      return "nil";
    }

//    return Arrays.toString(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }

  public String toString1() {
    if (this.equals(Key.getNullKey())){
      return "nil";
    }

    return Arrays.toString(bytes);
  }

  public byte[] getBytes() {
    return bytes;
  }

  public void setBytes(byte[] bytes) {
    this.bytes = bytes;
  }

  public boolean isNull() {
    return bytes == null;
  }

  /**
   * flip the most significant bit of each byte.
   */
  private byte[] flipMostSignificantBits(byte[] bytes1) {
    byte[] tmp = bytes1.clone();

    for (int i = 0; i < bytes1.length; i ++) {
      tmp[i] = flipMostSignificantBit(tmp[i]);
    }
    return tmp;
  }

  /**
   * flip the most significant bit of the first byte.
   * @return
   */
  private byte[] flipMostSignificantBit(byte[] byteArray) {
    byte[] tmp = byteArray.clone();
    tmp[0] = flipMostSignificantBit(tmp[0]);
    return tmp;
  }

  /**
   * flip the most significant bit of a byte.
   * @param byte1
   * @return
   */
  private byte flipMostSignificantBit(byte byte1) {
    return (byte) (byte1 ^ 0x80);
  }
}