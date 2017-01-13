package water.util;

import java.util.Arrays;

/**
 * Stores an immutable set of bytes, for fast evaluation.
 * 
 * Created by vpatryshev on 1/13/17.
 */
public class SetOfBytes {
  private boolean[] bits = new boolean[256];
  
  public SetOfBytes(byte[] bytes) {
    for (byte b : bytes) bits[0xff&b] = true;  
  }
  
  public SetOfBytes(String s) {
    this(s.getBytes());
  }
  
  public boolean contains(int b) { return b < 256 && b > -129 && bits[0xff&b];}
  
  public boolean equals(Object other) {
    return other instanceof SetOfBytes && 
        Arrays.equals(bits, ((SetOfBytes)other).bits);
  }
  
  public int hashCode() {
    return Arrays.hashCode(bits);
  }
  
  public int size() {
    int n = 0;
    for (int b = 0; b < 256; b++) if (bits[b]) n++;
    return n;
  }
  
  public byte[] getBytes() {
    byte[] out = new byte[size()];
    int i = 0;
    for (int b = 0; b < 256; b++) if (bits[b]) out[i++] = (byte)b;
    
    return out;
  }
  
  @Override public String toString() {
    return "SetOfBytes(" + Arrays.toString(getBytes()) + ")";
  }
}
