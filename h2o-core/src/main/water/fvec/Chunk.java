package water.fvec;

import water.Iced;
import water.Key;

public class Chunk extends Iced {
  public long _start;         // Start element; filled after AutoBuffer.read
  public int _len;            // Number of elements in this chunk
  public Key _veckey;         // Owning Vec; filled after AutoBuffer.read
  byte[] _mem; // Short-cut to the embedded memory; WARNING: holds onto a large array

  public final byte[] getBytes() { return _mem; }
}
