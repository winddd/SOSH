package parse;

import history.KVHistory;
import java.io.IOException;
import java.util.function.BiFunction;
import util.Context;

public interface Parser extends BiFunction<Context, String, KVHistory> {
  /**
   * @param folder
   * @return
   * @throws IOException
   */
  KVHistory parse(String folder);
}
