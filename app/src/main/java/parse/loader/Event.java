package parse.loader;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Event<KeyType, ValueType> {
  @EqualsAndHashCode.Include
  private final Transaction<KeyType, ValueType> transaction;
  @EqualsAndHashCode.Include
  private final Event.EventType type;
  @EqualsAndHashCode.Include
  private final KeyType key;
  @EqualsAndHashCode.Include
  private final ValueType value;

  @Override
  public String toString() {
    return String.format("%s(%s, %s)", type, key, value);
  }

  public enum EventType {
    READ, WRITE
  }
}
