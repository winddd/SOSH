package graphs;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import common.Key;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.graphs.BoomslangGraph;

/**
 * A utility class to dump BoomslangGraph into a text file.
 */
public class BoomslangGraphDumper {
    private BoomslangGraph graph;

    public BoomslangGraphDumper(BoomslangGraph graph) {
        this.graph = graph;
    }

    /**
     * Dumps the graph into a text file with the following format:
     * - Nodes section: lists all nodes
     * - Edges section: lists all edges with their types and keys
     * - Superpositions section: lists all superpositions
     * - Implies section: lists all implies
     * 
     * @param filePath Path to the output file
     * @throws IOException If there's an error writing to the file
     */
    public void dumpToFile(String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            // Write nodes
            writer.write("=== Nodes ===\n");
            for (int node : graph.getNodes()) {
                writer.write(node + "\n");
            }
            writer.write("\n");

            // Write edges
            writer.write("=== Edges ===\n");
            var adjList = graph.getAdjList();
            for (var u : adjList.keySet()) {
                Integer encodedU = u.txnId();

                for (var v : adjList.get(u).keySet()) {
                    Integer encodedV = v.txnId();

                    var pairs = adjList.get(u).get(v);
                    if (!pairs.isEmpty()) {
                        writer.write(String.format("%d -> %d [", encodedU, encodedV));
                        var pairStrings = pairs.stream()
                            .map(p -> String.format("(%s, %s)", p.getLeft(), p.getRight()))
                            .collect(java.util.stream.Collectors.toList());
                        writer.write(String.join("; ", pairStrings));
                        writer.write("]\n");
                    }
                }
            }
            writer.write("\n");

            // Write superpositions
            writer.write("=== Superpositions ===\n");
            for (Superposition superposition : graph.getSuperpositions()) {
                writer.write(superpositionToString(superposition) + "\n");
            }
            writer.write("\n");

            // Write implies
            writer.write("=== Implies ===\n");
            for (Imply imply : graph.getImplies()) {
                writer.write(implyToString(imply) + "\n");
            }
        }
    }

    private String superpositionToString(Superposition superposition) {
        List<String> edgeSets = new ArrayList<>();
        for (Set<TypeEdge> edgeSet : superposition.getEdgeSets()) {
            List<String> edges = new ArrayList<>();
            for (TypeEdge edge : edgeSet) {
                edges.add(String.format("(%d -> %d [%s, %s])", 
                    edge.u, edge.v, edge.edgeType, edge.key));
            }
            edgeSets.add("[" + String.join(", ", edges) + "]");
        }
        return String.join(" | ", edgeSets);
    }

    private String implyToString(Imply imply) {
        // TODO: Implement proper imply string representation
        return "Imply: " + imply.toString();
    }
} 