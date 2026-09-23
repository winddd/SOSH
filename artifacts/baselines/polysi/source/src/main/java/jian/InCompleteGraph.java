package jian;

import common.Key;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public abstract class InCompleteGraph {
    protected Set<Integer> nodes;
    protected Map<Integer, Map<Integer, Set<Pair<EdgeType, Key>>>> adjList;
    Constraints cons;
    //  protected Set<TypeEdge> edges;
    // an indicator of whether the edgeList hasn't been updated after the edges have
    // been updated.

    public InCompleteGraph(Set<Integer> nodes) {
        this.nodes = nodes;
        adjList = new HashMap<>();
        for (int u : this.nodes) {
            adjList.put(u, new HashMap<>());
        }

        cons = new Constraints();
    }

    /*
     * BCPolygraph should override this function to return `nodes` instead of
     * `txns`.
     */
    public Set<Integer> getNodes() {
        return this.nodes;
    }

    public Map<Integer, Map<Integer, Set<Pair<EdgeType, Key>>>> getAdjList() {
        return this.adjList;
    }

    public abstract void dump(String filePath);

//    public int numGeneralCons(){
//        int n = 0;
//        if (this instanceof HasGeneralConstraints) {
//            n = ((HasGeneralConstraints) this).getGeneralCons().size();
//        }
//        return n;
//    }
}

