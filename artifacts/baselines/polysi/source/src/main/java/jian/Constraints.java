package jian;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Constraints {
    protected Set<BinaryConstraint> cons;

    public Constraints() {
        cons = new HashSet<>();
    }

    public List<BinaryConstraint> getConstraints() {
        return new ArrayList<>(cons);
    }

    public void removeConstrain(BinaryConstraint con) {
        cons.remove(con);
    }

    public void addConstraint(BinaryConstraint con) {
        // deduplicate by normal form
//    assert con.es1.compareTo(con.es2) < 0;
        cons.add(con);
    }

    public int size() {
        return cons.size();
    }
}
