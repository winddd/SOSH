package parse.juicefs.operation;

import java.util.List;

public class IterOp {
  public byte[] start_key;
  public byte[] end_key;
  public List<byte[]> keys;
  public List<byte[]> vals;
}
