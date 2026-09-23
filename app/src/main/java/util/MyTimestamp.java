package util;

import java.sql.Timestamp;

public class MyTimestamp {
  public static long getTimestamp() {
    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    return timestamp.getTime();
  }
}
