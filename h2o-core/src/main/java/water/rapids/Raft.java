package water.rapids;

import water.DKV;
import water.Iced;
import water.Key;
import water.Value;
import water.fvec.Frame;

public class Raft extends Iced {
  private String _ast;
  private Key _key;

  public void set_ast(String ast) { _ast = ast; }
  public void set_key(Key key) { _key = key; }

  public String get_ast() { return _ast; }
  public Key get_key() { return _key; }

  public void remove() {
    Value v = DKV.remove(_key);
    if (v != null) {
      try {
        ((Frame)v.get()).delete();
      } catch (ClassCastException e) {
        throw new IllegalArgumentException("Failed to delete an object of type: "+(v.get().getClass()));
      }
    }
  }
}