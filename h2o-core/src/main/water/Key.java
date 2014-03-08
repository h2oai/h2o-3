package water;

public class Key {
  public byte[] _kb;            // Key bytes, wire-line protocol
  final public boolean isChunkKey() { return false; }
  public Key getVecKey() { return null; }
  public final boolean user_allowed() { return false; }
  public static Key make( byte[]kb ) { return null; }
}
