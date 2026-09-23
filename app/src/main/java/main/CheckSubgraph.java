package main;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import encoders.Encoder;
import encoders.EncoderFactory;
import graphs.graphs.InCompleteGraph;
import solvers.Solver;
import util.Config;
import util.Utils;
import util.exception.InvalidInputException;
import util.io.DumpResult;

public class CheckSubgraph {
  public static void main(String[] args) {
//    DataOutputStream stream = new DataOutputStream(System.out);
//    System.out.println("Child args: " + args);
    if(args.length != 2) {
      throw new InvalidInputException("CheckSubgraph subprocess got wrong number of parameters");
    }
    int graphIndex = Integer.parseInt(args[0]);
    int subgraphId = Integer.parseInt(args[1]);
    // String BOOMSLANG_HOME = "/home/windkl/git_repos/Boomslang";
    // Path filePath = Paths.get(BOOMSLANG_HOME, "subgraph"+subgraphId+".log");
    // DumpResult.writeToFile("Start checking subgraph "+subgraphId,
    //     filePath.toAbsolutePath().toString(), true);
   boolean ret = checkSubgraph(graphIndex, subgraphId);
   System.out.println(ret? "SAT" : "UNSAT");
  }

  public static boolean checkSubgraph(int graphIndex, int subgraphId) {
    System.out.println("Checking graph " + graphIndex + " subgraph " + subgraphId);
    // load from /tmp/subgraphId and deserialize it to be an IncompleteGraph and check it
    Config cfg = (Config) Utils.deserialize("/tmp/boomslang_cfg.ser");
    String subgraphFilename = "/tmp/"+ graphIndex + "." + subgraphId+".ser";
    System.out.printf("Deserializing from " + subgraphFilename + "\n");
    InCompleteGraph graph = (InCompleteGraph) Utils.deserialize(subgraphFilename);
    if (graph == null) {
      System.err.printf("Subgraph %d is null\n", subgraphId);
    }
    Encoder encoder = EncoderFactory.getEncoder(List.of(graph), "reverse encoding", cfg);
    Solver solver = encoder.encode();
    boolean ret = solver.solve();
    String BOOMSLANG_HOME = System.getenv("BOOMSLANG_HOME");
    // String BOOMSLANG_HOME = "/home/windkl/git_repos/Boomslang";
    Path filePath = Paths.get(BOOMSLANG_HOME, String.format("subgraph%d.%d.log", graphIndex, subgraphId));
    DumpResult.writeToFile(ret+"", filePath.toAbsolutePath().toString(), true);
    return ret;
  }
}
