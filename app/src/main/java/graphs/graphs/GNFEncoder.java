package graphs.graphs;

import common.Key;
import graphs.constraints.Imply;
import graphs.constraints.Superposition;
import graphs.edges.EdgeType;
import graphs.edges.TypeEdge;
import graphs.nodes.GraphNodeId;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * Encodes {@link BoomslangGraph} instances into GNF (Graph Normal Form) format for MonoSAT.
 *
 * <p>GNF (Graph Normal Form) is an extension of CNF (Conjunctive Normal Form) that integrates
 * graph-theoretic constraints with boolean SAT clauses. It allows encoding dependency graphs
 * alongside logical constraints, enabling SMT solvers like MonoSAT to reason about both graph
 * structure and boolean formulas simultaneously.
 *
 * <p><b>GNF Format Structure:</b>
 * <pre>
 * p cnf &lt;numVariables&gt; &lt;numClauses&gt;
 * edge &lt;graphId&gt; &lt;from&gt; &lt;to&gt; &lt;edgeVar&gt;
 * edge &lt;graphId&gt; &lt;from&gt; &lt;to&gt; &lt;edgeVar&gt;
 * ...
 * acyclic &lt;graphId&gt; &lt;acyclicVar&gt;
 * &lt;clause1&gt; 0
 * &lt;clause2&gt; 0
 * ...
 * </pre>
 *
 * <p><b>Encoding Components:</b>
 * <ol>
 *   <li><b>Header:</b> CNF header specifying total variables and clauses</li>
 *   <li><b>Digraph Section:</b> Edge declarations mapping each graph edge to a SAT variable</li>
 *   <li><b>Acyclic Constraint:</b> Boolean variable asserting the graph is acyclic</li>
 *   <li><b>CNF Section:</b> Boolean clauses encoding superpositions, implications, and constraints</li>
 * </ol>
 *
 * <p><b>Variable Assignment:</b>
 * <ul>
 *   <li>Each edge in the graph is assigned a unique SAT variable (integer ≥ 1)</li>
 *   <li>Superpositions introduce auxiliary variables for edge set choices</li>
 *   <li>Implications are encoded directly as CNF clauses</li>
 *   <li>Deterministic ordering ensures reproducible variable assignment</li>
 * </ul>
 *
 * <p><b>Superposition Encoding:</b> A superposition representing "choose exactly one of
 * {edgeSet1, edgeSet2, ..., edgeSetN}" is encoded as:
 * <ul>
 *   <li>For each edge set, create an auxiliary variable representing "this set is chosen"</li>
 *   <li>Add Tseitin transformation clauses linking edge set variables to individual edges</li>
 *   <li>Add "at least one" clause: (edgeSet1 ∨ edgeSet2 ∨ ... ∨ edgeSetN)</li>
 *   <li>Add "at most one" clauses: pairwise exclusion (¬edgeSetI ∨ ¬edgeSetJ) for all I ≠ J</li>
 * </ul>
 *
 * <p><b>Implication Encoding:</b> An implication (wrEdge ∧ wwEdge) ⇒ rwEdge is encoded as
 * the CNF clause: (¬wrEdge ∨ ¬wwEdge ∨ rwEdge).
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * BoomslangGraph graph = ...; // Construct graph with superpositions and implications
 * GNFEncoder.GNFResult result = GNFEncoder.encodeGNF(graph);
 * String gnfString = result.toGNF();
 *
 * // Write to file for MonoSAT solver
 * Files.writeString(Path.of("instance.gnf"), gnfString);
 *
 * // Profiling information
 * System.out.printf("Edges: %d, Variables: %d, Clauses: %d%n",
 *     result.numEdges, result.numCnfVariables, result.numCnfClauses);
 * }</pre>
 *
 * @see BoomslangGraph
 * @see graphs.constraints.Superposition
 * @see graphs.constraints.Imply
 */
public class GNFEncoder {

    /**
     * Result of GNF encoding containing both the digraph and CNF parts.
     */
    public static class GNFResult {
        public final String digraphSection;
        public final String cnfSection;
        public final long numEdges;
        public final long numCnfVariables;
        public final long numCnfClauses;

        public GNFResult(String digraphSection, String cnfSection,
                        long numEdges, long numCnfVariables, long numCnfClauses) {
            this.digraphSection = digraphSection;
            this.cnfSection = cnfSection;
            this.numEdges = numEdges;
            this.numCnfVariables = numCnfVariables;
            this.numCnfClauses = numCnfClauses;
        }

        /**
         * Returns the complete GNF string with header.
         */
        public String toGNF() {
            StringBuilder sb = new StringBuilder();
            sb.append("p cnf ").append(numCnfVariables).append(" ").append(numCnfClauses).append("\n");
            sb.append(digraphSection);
            sb.append(cnfSection);
            return sb.toString();
        }
    }

    /**
     * Encodes a BoomslangGraph into GNF format.
     *
     * @param graph The graph to encode
     * @return GNFResult containing the encoded graph
     */
    public static GNFResult encodeGNF(BoomslangGraph graph) {
        StringBuilder digraphBuilder = new StringBuilder();
        StringBuilder cnfBuilder = new StringBuilder();

        long varIndex = 1; // MonoSAT variables start from 1
        long numCnfClauses = 0;

        // Map from TypeEdge to its SAT variable
        // Use LinkedHashMap to ensure deterministic iteration order
        Map<TypeEdge, Long> edgeVarMap = new LinkedHashMap<>();

        // Step 1: Collect all known edges from adjacency list and sort them for deterministic ordering
        List<TypeEdge> knownEdges = new ArrayList<>();
        for (Map.Entry<GraphNodeId, Map<GraphNodeId, Set<Pair<EdgeType, Key>>>> fromEntry :
             graph.adjList.entrySet()) {
            GraphNodeId from = fromEntry.getKey();

            for (Map.Entry<GraphNodeId, Set<Pair<EdgeType, Key>>> toEntry :
                 fromEntry.getValue().entrySet()) {
                GraphNodeId to = toEntry.getKey();

                for (Pair<EdgeType, Key> edgeLabel : toEntry.getValue()) {
                    EdgeType edgeType = edgeLabel.getLeft();
                    Key key = edgeLabel.getRight();

                    TypeEdge typeEdge = new TypeEdge(from, to, edgeType, key);
                    knownEdges.add(typeEdge);
                }
            }
        }

        // Sort edges by (u, v) to ensure deterministic variable assignment
        knownEdges.sort((e1, e2) -> {
            int cmp = Integer.compare(e1.u, e2.u);
            if (cmp != 0) return cmp;
            return Integer.compare(e1.v, e2.v);
        });

        // Allocate variables for sorted known edges
        for (TypeEdge typeEdge : knownEdges) {
            if (!edgeVarMap.containsKey(typeEdge)) {
                edgeVarMap.put(typeEdge, varIndex++);
            }
        }

        // Step 2: Encode superpositions (1-out-of-n constraints)
        // Each superposition represents: exactly one of the edge sets must be present
        for (Superposition superposition : graph.getSuperpositions()) {
            List<Long> edgeSetVars = new ArrayList<>();

            for (Set<TypeEdge> edgeSet : superposition.getEdgeSets()) {
                // Allocate a fresh variable for this edge set
                long edgeSetVar = varIndex++;
                edgeSetVars.add(edgeSetVar);

                List<Long> edgeVars = new ArrayList<>();

                for (TypeEdge edge : edgeSet) {
                    // Allocate or retrieve edge variable
                    long edgeVar;
                    if (edgeVarMap.containsKey(edge)) {
                        edgeVar = edgeVarMap.get(edge);
                    } else {
                        edgeVar = varIndex++;
                        edgeVarMap.put(edge, edgeVar);
                    }
                    edgeVars.add(edgeVar);

                    // Tseitin transformation: edgeSetVar => edgeVar
                    // Encoded as: ¬edgeSetVar ∨ edgeVar
                    cnfBuilder.append(-edgeSetVar).append(" ").append(edgeVar).append(" 0\n");
                    numCnfClauses++;
                }

                // Reverse direction: (edge1 ∧ edge2 ∧ ... ∧ edgeN) => edgeSetVar
                // Encoded as: edgeSetVar ∨ ¬edge1 ∨ ¬edge2 ∨ ... ∨ ¬edgeN
                cnfBuilder.append(edgeSetVar);
                for (long edgeVar : edgeVars) {
                    cnfBuilder.append(" ").append(-edgeVar);
                }
                cnfBuilder.append(" 0\n");
                numCnfClauses++;
            }

            // At least one edge set must be chosen: edgeSet1 ∨ edgeSet2 ∨ ... ∨ edgeSetN
            for (long edgeSetVar : edgeSetVars) {
                cnfBuilder.append(edgeSetVar).append(" ");
            }
            cnfBuilder.append("0\n");
            numCnfClauses++;

            // At most one edge set can be chosen (pairwise exclusion)
            // For each pair (i, j) where i ≠ j: ¬edgeSetI ∨ ¬edgeSetJ
            for (int i = 0; i < edgeSetVars.size(); i++) {
                for (int j = i + 1; j < edgeSetVars.size(); j++) {
                    cnfBuilder.append(-edgeSetVars.get(i)).append(" ")
                              .append(-edgeSetVars.get(j)).append(" 0\n");
                    numCnfClauses++;
                }
            }
        }

        // Step 3: Encode implication constraints (WR ∧ WW => RW)
        // This is specific to Boomslang and not in the original C++ version
        for (Imply imply : graph.getImplies()) {
            // Get or allocate variables for the three edges
            long wrVar = getOrAllocateEdgeVar(imply.wrEdge, edgeVarMap, varIndex);
            if (wrVar >= varIndex) varIndex = wrVar + 1;

            long wwVar = getOrAllocateEdgeVar(imply.wwEdge, edgeVarMap, varIndex);
            if (wwVar >= varIndex) varIndex = wwVar + 1;

            long rwVar = getOrAllocateEdgeVar(imply.rwEdge, edgeVarMap, varIndex);
            if (rwVar >= varIndex) varIndex = rwVar + 1;

            // Encode: (WR ∧ WW) => RW
            // Equivalent to: ¬WR ∨ ¬WW ∨ RW
            cnfBuilder.append(-wrVar).append(" ").append(-wwVar).append(" ")
                      .append(rwVar).append(" 0\n");
            numCnfClauses++;
        }

        // Step 4: Output all edges to digraph section
        for (Map.Entry<TypeEdge, Long> entry : edgeVarMap.entrySet()) {
            TypeEdge edge = entry.getKey();
            long edgeVar = entry.getValue();

            // Format: edge <graph_id> <from> <to> <var>
            // graph_id is 0 for the main graph
            digraphBuilder.append("edge 0 ")
                         .append(edge.u).append(" ")
                         .append(edge.v).append(" ")
                         .append(edgeVar).append("\n");
        }

        // Step 5: Add acyclic constraint
        // Format: acyclic <graph_id> <var>
        long acyclicVar = varIndex++;
        digraphBuilder.append("acyclic 0 ").append(acyclicVar).append("\n");

        long numEdges = edgeVarMap.size();
        long numCnfVariables = varIndex - 1;

        return new GNFResult(
            digraphBuilder.toString(),
            cnfBuilder.toString(),
            numEdges,
            numCnfVariables,
            numCnfClauses
        );
    }

    /**
     * Helper method to get or allocate a variable for an edge.
     */
    private static long getOrAllocateEdgeVar(TypeEdge edge,
                                            Map<TypeEdge, Long> edgeVarMap,
                                            long nextVar) {
        if (edgeVarMap.containsKey(edge)) {
            return edgeVarMap.get(edge);
        } else {
            long newVar = nextVar;
            edgeVarMap.put(edge, newVar);
            return newVar;
        }
    }
}
