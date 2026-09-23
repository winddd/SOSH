package asg;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;

import graphs.DirectDep;
import graphs.edges.TypeEdge;

/**
 * A utility class to dump GraphIR into a text file.
 */
public class GraphIRDumper {
  private ASG ASG;

  public GraphIRDumper(ASG ASG) {
    this.ASG = ASG;
  }

  /**
   * Dumps the GraphIR into a text file with the following format:
   * - Edges section: lists all edges with their types and keys
   * - DirectDeps section: lists all direct dependencies
   *
   * @param filePath Path to the output file
   * @throws IOException If there's an error writing to the file
   */
  public void dumpToFile(String filePath) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
      // Write edges
      writer.write("=== Edges ===\n");
      Collection<TypeEdge> edges = ASG.getDependencyStore().edges();
      for (TypeEdge edge : edges) {
        writer.write(String.format("%d -> %d [%s, %s]\n",
            edge.u, edge.v, edge.edgeType, edge.key));
      }
      writer.write("\n");

      // Write directDeps
      writer.write("=== DirectDeps ===\n");
      Collection<DirectDep> directDeps = ASG.getDependencyStore().directDeps();
      for (DirectDep dep : directDeps) {
        writer.write(directDepToString(dep) + "\n");
      }
    }
  }

  private String directDepToString(DirectDep dep) {
    var candidateStr = dep.externalCandidateWrites().stream()
        .map(cw -> String.format("%d@%s", cw.getTxnId(), cw.getValue()))
        .collect(Collectors.joining(", ", "[", "]"));
    return String.format("ReadTxn: %d, Key: %s, EdgeType: %s, CandidateWrites: %s, AllWrites: %s, isRMW: %b",
        dep.txnId,
        dep.key,
        dep.edgeType,
        candidateStr,
        dep.allWriteTxnIds.stream().map(Object::toString).collect(Collectors.joining(", ", "[", "]")),
        dep.isRMW);
  }
}
