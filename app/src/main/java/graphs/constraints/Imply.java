package graphs.constraints;

import graphs.edges.TypeEdge;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(of={"wrEdge", "wwEdge", "rwEdge"})
public class Imply implements Serializable {
  public TypeEdge wrEdge;
  public TypeEdge wwEdge;
  public TypeEdge rwEdge;

  public Imply(TypeEdge wrEdge, TypeEdge wwEdge, TypeEdge rwEdge) {
    this.wrEdge = wrEdge;
    this.wwEdge = wwEdge;
    this.rwEdge = rwEdge;
  }

  public Imply toBCImply() {
    var newWrEdge = wrEdge.toBCEdge();
    var newWwEdge = wwEdge.toBCEdge();
    var newRwEdge = rwEdge.toBCEdge();
    return new Imply(newWrEdge, newWwEdge, newRwEdge);
  }

  public String toString() {
    return String.format("Imply: (%s AND %s ==> %s)", wrEdge, wwEdge, rwEdge);
  }
}
