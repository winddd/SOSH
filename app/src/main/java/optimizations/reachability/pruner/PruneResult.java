package optimizations.reachability.pruner;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PruneResult {
  public boolean hasCycle;
  public int numSolvedBinaryCons;
  public int numSolvedGeneralCons;
  public int numSolvedSuperpositions;
  public int numSimplifiedSuperpositions;

  public int solvedProgress() {
    return numSolvedBinaryCons + numSolvedGeneralCons + numSolvedSuperpositions;
  }
}
