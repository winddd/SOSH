//package util.io;
//
//import history.KVHistory;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import parse.cobra.CobraHistoryParser;
//import util.Config;
//
//public class KvHistory2ViperLog {
//  public static void main(String[] args) {
////    CobraHistoryParser loader = new CobraHistoryParser();
////    Config.loadConfig("/home/windkl/git_repos/Boomslang/core/config.yaml");
////    KVHistory history = loader.parse("/home/windkl/git_repos/PolySI-PVLDB2023-Artifacts" +
////        "/artifact/PolySIHistories/fig8_9_10/rubis-10000/hist-00000", false);
////    ViperLogWriter viperLogWriter = new ViperLogWriter(history,
////        Paths.get("/home/windkl/git_repos/Boomslang/viper_logs"));
////    viperLogWriter.dump();
//    String prefix = "/home/windkl/viper_logs/boomslang_eval/comparison";
//    String[] cobraLogDirs = new String[]{
////        "chengTxn/chengTxn_2k",
////        "chengTxn/chengTxn_5k",
//        "rubis_2k",
//        "rubis_5k",
//        "tpcc_2k",
//        "tpcc_5k",
//        "twitter_2k",
//        "twitter_5k",
//        "ycsb_2k",
//        "ycsb_5k",
//        "tpcc_10k",
//        "twitter_10k",
//        "ycsb_10k"
//    };
//    Config.loadConfig("/home/windkl/git_repos/Boomslang/core/config.yaml");
//    for(var subDir : cobraLogDirs) {
//      CobraHistoryParser loader = new CobraHistoryParser();
//
//      Path path = Paths.get(prefix, subDir);
//      KVHistory history = loader.parse(path.toString(), false);
//      ViperLogWriter viperLogWriter = new ViperLogWriter(history,
//        Paths.get("/home/windkl/git_repos/Viper_series/Viper_public/history_data2/logs/cobra",
//            subDir));
//      viperLogWriter.dump();
//    }
//  }
//}
