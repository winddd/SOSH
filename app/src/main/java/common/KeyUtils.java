package common;

public class KeyUtils {
  protected static final byte signMaskByte = (byte) 0x80;
  protected static final int signMaskInt = 0x80000000;
  protected static final long signMaskLong = 0x8000000000000000L;
  protected static Key maxKey = new Key(
      new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255});
}
