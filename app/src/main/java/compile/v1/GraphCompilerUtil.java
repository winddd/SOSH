package compile.v1;

import asg.ASG;
import asg.WriteEvent;
import compile.v2.inputs.GraphInputs;
import compile.v2.modules.ModuleUtility;
import util.Config;
import util.enumtypes.MODE;
import util.exception.RejectException;

import java.util.List;

public class GraphCompilerUtil {
  public static void checkIntermediateReads(ASG asg, MODE mode, Config cfg){
    var graphInputs = GraphInputs.from(asg);
    for (var directDep : asg.getDependencyStore().directDeps()) {
      var txn = graphInputs.history().getKthTxn(directDep.txnId);
      List<WriteEvent> writeEvents = ModuleUtility.resolveCandidateWritesWithRyowPolicy(directDep, txn, mode, cfg.RYOW_POLICY);
      if (writeEvents.isEmpty()) {
        throw new RejectException("No candidate writes");
      }
    }
  }
}
