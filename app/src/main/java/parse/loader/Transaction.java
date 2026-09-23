package parse.loader;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Transaction<KeyType, ValueType> {
  @EqualsAndHashCode.Include
  final long id;
  final List<Event<KeyType, ValueType>> events = new ArrayList<>();
  @EqualsAndHashCode.Include
  private final Session<KeyType, ValueType> session;
  private Transaction.TransactionStatus status = Transaction.TransactionStatus.ONGOING;

  @Override
  public String toString() {
    return String.format("(%d, %d)", session.getId(), id);
  }

  public enum TransactionStatus {
    ONGOING, COMMIT
  }
}
