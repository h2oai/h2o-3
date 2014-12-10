package water.rapids;

import water.DKV;
import water.Iced;
import water.Key;
import water.Value;
import water.fvec.Frame;

public class Raft extends Iced {
  AST _ast;
  Key _key;

  public void set_ast(AST ast) { _ast = ast; }
  public void set_key(Key key) { _key = key; }

  public Key    get_key() { return _key; }
  public AST get_ast() { return _ast; }


  public void delete() {
    if (_key == null ) return;
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