package common.binaryhistory;

import java.io.Serializable;
import java.util.List;

import lombok.Getter;

@Getter
public class LOG_TXN implements Serializable {
  private static final long serialVersionUID = 6529685098267757692L;
  private boolean isInitial;
  private boolean isFinal;
  private List<Mop> mops;

  public LOG_TXN (boolean isInitial, boolean isFinal, List<Mop> mops) {
    this.isInitial = isInitial;
    this.isFinal = isFinal;
    this.mops = mops;
  }
}
