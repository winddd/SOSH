//package tools.anomaly_injector;
//
//import java.io.IOException;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.Set;
//import parse.cobra.CobraHistoryParser;
//import util.Config;
//
///**
// * edit a history and dump it into files.
// * used for bug injection.
// */
//public class BinaryHistoryEditorMain {
//  private static void injectG1c(Path logDir, int n) {
//    UnsatCoreInjector injector = new G1cCoreInjector();
//    // load logs
//    var session2Txns = injector.load(logDir);
//    Set<Integer> tids = injector.injectUnsatCores(session2Txns, n);
//    injector.dump(session2Txns, tids, logDir.toString(), logDir.toString() + "_G1c_" + n);
//    return;
//  }
//
//  private static void injectGSIb(Path logDir, int n) {
//    UnsatCoreInjector injector = new GSIbInjector();
//    var m = injector.load(logDir);
//    Set<Integer> tids = injector.injectUnsatCores(m, n);
//    injector.dump(m, tids, logDir.toString(), logDir.toString() + "_GSIb");
//    return;
//  }
//
//  static void injectNonRepeatableReads(Path logDir, int n) {
//    UnsatCoreInjector injector = new NonRepeatableReadInjector();
//    var m = injector.load(logDir);
//    Set<Integer> tids = injector.injectUnsatCores(m, n);
//    injector.dump(m, tids, logDir.toString(), logDir.toString() + "_NRR_" + n);
//    return;
//  }
//
//  static void injectThinAirReads(Path logDir, int n) {
//    UnsatCoreInjector injector = new ThinAirReadInjector();
//    var m = injector.load(logDir);
//    Set<Integer> tids = injector.injectUnsatCores(m, n);
//    injector.dump(m, tids, logDir.toString(), logDir.toString() + "_TAR");
//    return;
//  }
//
//  static void injectLongForks(Path logDir, int n) {
//    UnsatCoreInjector injector = new LongForkInjector();
//    var m = injector.load(logDir);
//    Set<Integer> tids = injector.injectUnsatCores(m, n);
//    injector.dump(m, tids, logDir.toString(), logDir.toString() + "_LF_" + n);
//    return;
//  }
//
//  static void injectChengUnsatCores(Path logDir, int n) {
////    UnsatCoreInjector injector = new ChengUnsatCoreInjector();
////    var m = injector.load(logDir);
////    Set<Integer> tids = injector.injectUnsatCores(m, n);
////    injector.dump(m, tids, logDir.toString(), logDir.toString() + "_ChengUnsat_" + n);
//  }
//
//  public static void main(String[] args) throws IOException {
//    if (args.length != 1) {
//      System.err.println("Cmd args error");
//      System.exit(-1);
//    }
//
//    CobraHistoryParser loader = new CobraHistoryParser();
//    Config.loadConfig("/home/windkl/git_repos/Boomslang/config.yaml");
//
//    String historyFolder = args[0];
////    injectG1c(Paths.get(historyFolder), 100);
////    injectGSIb(Paths.get(historyFolder), 1);
////    injectNonRepeatableReads(Paths.get(historyFolder), 100);
////    injectThinAirReads(Paths.get(historyFolder), 1);
//    injectChengUnsatCores(Paths.get(historyFolder), 100);
//  }
//}
