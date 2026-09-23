package jian;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Set;
import java.util.stream.Collectors;

@EqualsAndHashCode(of={"edgeSet1", "edgeSet2"})
public class BinaryConstraint {
    @Getter
    private Set<TypeEdge> edgeSet1;
    @Getter
    private Set<TypeEdge> edgeSet2;
    private boolean isBCConstraint;

    public BinaryConstraint(Set<TypeEdge> edgeSet1, Set<TypeEdge> edgeSet2) {
        this(edgeSet1, edgeSet2, false);
    }

    public BinaryConstraint(Set<TypeEdge> edgeSet1, Set<TypeEdge> edgeSet2, boolean isBCConstraint) {
        this.edgeSet1 = edgeSet1;
        this.edgeSet2 = edgeSet2;
        this.isBCConstraint = isBCConstraint;
    }

    public BinaryConstraint(TypeEdge e1, TypeEdge e2) {
        this(e1, e2, false);
    }

    public BinaryConstraint(TypeEdge e1, TypeEdge e2, boolean isBCConstraint) {
        // convert into canoncial form.
        this(Set.of(e1), Set.of(e2), isBCConstraint);
    }

    private static boolean shouldSwap(TypeEdge e1, TypeEdge e2) {
        return e1.compareTo(e2) >= 0;
    }

    public boolean isBCConstraint() {
        return isBCConstraint;
    }
}

