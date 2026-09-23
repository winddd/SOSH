package optimizations.unsat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import graphs.graphs.InCompleteGraph;
import graphs.nodes.GraphNodeId;
import lombok.extern.slf4j.Slf4j;
import optimizations.SearchResult;
import util.Config;
import util.Profiler;
import util.Utils;

/**
 * chop the graph into multiple subgraphs,
 * if any subgraph returns UNSAT, return UNSAT immediately.
 */
@Slf4j
public class ReverseSearch {
  private final List<InCompleteGraph> graphs;
  private boolean debug;
  private int id;
  private Config cfg;
  // private Profiler profiler = Profiler.getInstance();

  public ReverseSearch(InCompleteGraph G,
      Config cfg,
      int id) {
    this(List.of(G), cfg, id);
  }

  public ReverseSearch(List<InCompleteGraph> graphs,
      Config cfg,
      int id) {
    if (graphs == null || graphs.isEmpty()) {
      throw new IllegalArgumentException("ReverseSearch requires at least one graph");
    }
    this.graphs = graphs;
    this.debug = cfg.DEBUG;
    this.id = id;
    this.cfg = cfg;
  }

  // public List<>
  public SearchResult search() {
    log.info("Start reverse search...");
    boolean allSat = true;
    int graphIndex = 0;
    for (InCompleteGraph graph : graphs) {
      List<Set<GraphNodeId>> nodesPartitions = GraphChopping.splitNodes(graph, cfg.NUM_SUBGRAPHS);
      Utils.serialize(cfg, "/tmp/boomslang_cfg.ser");
      for (int i = 0; i < nodesPartitions.size(); i++) {
        InCompleteGraph subgraph = GraphChopping.getInducedSubgraph(graph, nodesPartitions.get(i), cfg);
        Utils.serialize(subgraph, "/tmp/" + graphIndex + "." + i + ".ser");
        boolean sat = checkSubGraph(graphIndex, i);
        System.out.printf("graph %d subgraph %d: %b\n", graphIndex, i, sat);

        if (!sat) {
          return new SearchResult(false, id, Profiler.getInstance().getTAG2RUNTIME());
        }
      }
      graphIndex++;
    }

    return new SearchResult(allSat, id, Profiler.getInstance().getTAG2RUNTIME());
  }

  private boolean checkSubGraph(int graphIndex, int subgraphId) {
    Runtime runtime = Runtime.getRuntime();
    String BOOMSLANG_HOME = System.getenv("BOOMSLANG_HOME");
    String JAVA_HOME = System.getenv("JAVA_HOME");
    System.out.println(JAVA_HOME);
    String javaCmd = Paths.get(JAVA_HOME, "bin", "java").toString();

    String jarPath = Paths.get(BOOMSLANG_HOME, "app", "build", "libs", "app-1.0-SNAPSHOT-b.jar").toString();
    String jFlags = "-ea -Djava.library.path=/usr/local/lib";
    boolean ret = false;
    try {
      String cmd = String.format("%s %s -jar %s %d %d", javaCmd, jFlags, jarPath, graphIndex, subgraphId);
      System.out.println(cmd);
      Process process = runtime.exec(cmd);

      // Wait for the process to complete and get the exit value
      int exitCode = process.waitFor();
      System.out.println("Exited with code: " + exitCode);

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          System.out.println("From child process" + subgraphId + ": " + line);
          if (line.trim().equals("SAT") || line.trim().equals("UNSAT")) {
            ret = line.trim().equals("SAT") ? true : false;
            break;
          }
        }
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          System.out.println("From child process" + subgraphId + ": " + line);
          // if (line.trim().equals("SAT") || line.trim().equals("UNSAT")) {
          // ret = line.trim().equals("SAT") ? true : false;
          // break;
          // }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return ret;
  }
}
