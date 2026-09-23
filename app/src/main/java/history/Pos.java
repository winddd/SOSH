package history;

import lombok.Getter;

@Getter
public class Pos {
  private final int txnId;
  private final int mopIndex;
  public Pos(int txnId, int mopIndex) {
    this.txnId = txnId;
    this.mopIndex = mopIndex;
  }

  public boolean equals(Object o) {
    if (o == this)
      return true;
    if (o == null || o.getClass() != Pos.class)
      return false;

    return txnId == ((Pos) o).txnId && mopIndex == ((Pos) o).mopIndex;
  }
}
