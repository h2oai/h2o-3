package water.currents.transforms;

import water.Iced;
import water.currents.ASTExec;
import water.currents.ASTParameter;
import water.currents.Exec;
import water.fvec.Frame;
import water.util.IcedHashMap;

import java.util.HashMap;

public abstract class Transform<T> extends Iced {
  protected final String _name;
  protected final ASTExec _ast;
  protected final boolean _inplace;
  protected final IcedHashMap<String,ASTParameter> _params;

  Transform(String name, String ast, boolean inplace) {
    _name=name;
    _ast = (ASTExec)new Exec(ast).parse();
    _inplace = inplace;
    _params = new IcedHashMap<>();
  }

  public String name() { return _name; }

  abstract Transform<T> fit(Frame f);
  abstract Frame transform(Frame f);
  abstract Frame inverseTransform(Frame f);

  public Frame fitTransform(Frame f) { return fit(f).transform(f); }

  public HashMap<String, ASTParameter> getParams(boolean deep) {
    HashMap<String,ASTParameter> out = new HashMap<>();
    for( String K: _params.keySet() ) {
      ASTParameter V = _params.get(K);
//      if( deep && V instanceof Transform) {
//        HashMap<String, ASTParameter> deepItems = ((Transform)V).getParams(true);
//        for(String dK: deepItems.keySet()) {
//          ASTParameter o = deepItems.remove(dK);
//          deepItems.put(K+":"+dK,o);
//        }
//        out.putAll(deepItems);
//      } else {
        out.put(K, V);
//      }
    }
    return out;
  }

  Transform<T> setParams(HashMap<String, ASTParameter> params) {
    for(String K: params.keySet())
      _params.put(K,params.get(K));
    return this;
  }

  public abstract StringBuilder genClass();
}