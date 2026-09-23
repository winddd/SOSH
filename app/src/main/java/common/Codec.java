package common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Codec {
  public static byte[] internalEncodeLong(long n) {
    // https://stackoverflow.com/questions/4485128/how-do-i-convert-long-to-byte-and-back-in-java
    ByteBuffer longBuffer = ByteBuffer.allocate(Long.BYTES);
    // longBuffer.clear();
    longBuffer.putLong(0, n);
    return longBuffer.array();
  }

  public static byte[] encodeMultipleLong(long... ls) {
    int n = ls.length;
    ByteBuffer buf = ByteBuffer.allocate(Long.BYTES * n);
    for (int i = 0; i < n; i++) {
      buf.putLong(i * Long.BYTES, ls[i]);
    }

    return buf.array();
  }

  public static Key encodeLong(Long n) {
    if (n == null)
      return Key.getNullKey();
    return new Key(internalEncodeLong(n));
  }

  public static long decode(byte[] bytes) {
    long value = 0l;

    // Iterating through for loop
    for (byte b : bytes) {
      // Shifting previous value 8 bits to right and
      // add it with next value
      value = (value << 8) + (b & 255);
    }

    return value;
  }

  // public static byte[] encodeInteger(int n) {
  // //
  // https://stackoverflow.com/questions/4485128/how-do-i-convert-long-to-byte-and-back-in-java
  // ByteBuffer intBuffer = ByteBuffer.allocate(Integer.BYTES);
  // // intBuffer.clear();
  // intBuffer.putInt(0, n);
  // return intBuffer.array();
  // }

  // public static long decodeLong(byte[] bytes){
  // assert bytes.length >= Long.BYTES;
  // ByteBuffer longBuffer = ByteBuffer.allocate(Long.BYTES);
  //// longBuffer.clear();
  // longBuffer.put(bytes, 0, bytes.length);
  // longBuffer.flip();//need flip
  // long n = -1;
  // try{
  // n = longBuffer.getLong();
  // } catch(BufferUnderflowException e){
  // e.printStackTrace();
  // }
  // return n;
  // }
  //
  // public static int decodeInteger(byte[] bytes){
  // assert bytes.length >= Integer.BYTES;
  // ByteBuffer intBuffer = ByteBuffer.allocate(Integer.BYTES);
  //// intBuffer.clear();
  // intBuffer.put(bytes, 0, bytes.length);
  // intBuffer.flip();//need flip
  // return intBuffer.getInt();
  // }

}
