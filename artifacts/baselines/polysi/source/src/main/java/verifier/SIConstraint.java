package verifier;

import java.util.Collection;
import java.util.List;

import history.Transaction;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
class SIConstraint<KeyType, ValueType> {

    // writeTransaction1 -> writeTransaction2
//    @ToString.Include(rank = 1)
    private final Collection<SIEdge<KeyType, ValueType>> edges1;

    // writeTransaction2 -> writeTransaction1
//    @ToString.Include(rank = 0)
    private final Collection<SIEdge<KeyType, ValueType>> edges2;

//    @ToString.Include(rank = 3)
    private final Transaction<KeyType, ValueType> writeTransaction1;

//    @ToString.Include(rank = 2)
    private final Transaction<KeyType, ValueType> writeTransaction2;

    @EqualsAndHashCode.Include
    private final int id;

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Edges 1:\n");
        for (var e : edges1) {
            sb.append(e.toString() + "\n");
        }
        sb.append("Edges 2:\n");
        for (var e : edges2) {
            sb.append(e.toString() + "\n");
        }
        return sb.toString();
    }
}
