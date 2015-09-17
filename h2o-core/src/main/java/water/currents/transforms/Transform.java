package water.currents.transforms;

import hex.genmodel.GenModel;
import water.H2O;
import water.MRTask;
import water.currents.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.util.ArrayUtils;

import java.util.HashMap;

public abstract class Transform<T> {
  HashMap<String,Object> _params;
  Transform() { _params = new HashMap<>(); }
  Transform(HashMap<String, Object> params) { _params=params; }

  abstract Transform<T> fit(Frame f);
  abstract Frame transform(Frame f);
  abstract Frame inverseTransform(Frame f);

  public Frame fitTransform(Frame f) { return fit(f).transform(f); }

  public HashMap<String, Object> getParams(boolean deep) {
    HashMap<String,Object> out = new HashMap<>();
    for( String K: _params.keySet() ) {
      Object V = _params.get(K);
      if( deep && V instanceof Transform) {
        HashMap<String, Object> deepItems = ((Transform)V).getParams(true);
        for(String dK: deepItems.keySet()) {
          Object o = deepItems.remove(dK);
          deepItems.put(K+":"+dK,o);
        }
        out.putAll(deepItems);
      }
      out.put(K,V);
    }
    return out;
  }

  Transform<T> setParams(HashMap<String, Object> params) {
    for(String K: params.keySet())
      _params.put(K,params.get(K));
    return this;
  }

  public abstract StringBuilder genClass(String stepName);
}


class H2OScaler extends Transform<H2OScaler> {

  double[] means;
  double[] sdevs;

  @Override
  Transform<H2OScaler> fit(Frame f) {
    means = new double[f.numCols()];
    sdevs = new double[f.numCols()];
    for(int i=0;i<f.numCols();++i) {
      means[i] = f.vec(i).mean();
      sdevs[i] = f.vec(i).sigma();
    }
    return this;
  }

  // TODO: handle Enum, String, NA
  @Override Frame transform(Frame f) {
    final double[] fmeans = means;
    final double[] fmults = ArrayUtils.invert(sdevs);
    return new MRTask() {
      @Override public void map(Chunk[] cs, NewChunk[] ncs) {
        double[] in = new double[cs.length];
        for(int row=0; row<cs[0]._len; row++) {
          for(int col=0; col<cs.length; col++)
            in[col] = cs[col].atd(row);
          GenModel.scaleInPlace(fmeans,fmults,in);
          for(int col=0; col<ncs.length; col++)
            ncs[col].addNum(in[col]);
        }
      }
    }.doAll(f.numCols(),f).outputFrame(f.names(),f.domains());
  }

  @Override Frame inverseTransform(Frame f) { throw H2O.unimpl(); }
  @Override public StringBuilder genClass(String stepName) {
    throw H2O.unimpl();
  }
}

class H2OColSelect extends Transform<H2OColSelect> {
  private final ASTExec _ast;
  private final String _cols;

  H2OColSelect(ASTExec ast) {  // not a public constructor -- used by the REST api only;
    _ast=ast;  // (cols dummyID [ cols ])
    _cols = _ast._asts[2].toString();
  }

  @Override Transform<H2OColSelect> fit(Frame f) { return this; }
  @Override Frame transform(Frame f) { return Exec.execute(_ast).getFrame(); }
  @Override Frame inverseTransform(Frame f) { throw H2O.unimpl(); }
  public StringBuilder genClass(String stepName) {
    StringBuilder sb = new StringBuilder();
    String s = "public static class " + stepName + " extends Step<" + stepName + "> {\n" +
               "  private final String[] _cols = new String[]{"+ _cols +"};\n" +
               "  " + stepName + "() { _inplace=true; }\n" +
               "  @Override public RowData transform(RowData row) {\n" +
               "    RowData colSelect = new RowData();\n" +
               "    for(String s: _cols) \n" +
               "      colSelect.put(s, row.get(s));\n" +
               "    return colSelect;\n"+
               "  }\n"+
               "}\n";
    sb.append(s);
    return sb;
  }
}

class H2OColOp extends Transform {
  final ASTExec _ast;
  final boolean _inplace;
  final String _fun;
  String _newCol;

  H2OColOp(ASTExec ast, boolean inplace) { // (op (cols fr cols) {extra_args})
    _ast = ast;
    _inplace = inplace;
    _params = new HashMap<String, Object>();
    _fun = _ast.str();
    String[] args = _ast.getArgs();
    if( args!=null && args.length > 1 ) { // first arg is the frame
      for(int i=2; i<args.length; ++i)
        _params.put(args[i],_ast._asts[i+1]);
    }
  }

  @Override Transform fit(Frame f) { return this; }

  @Override Frame transform(Frame f) {
    ((ASTExec)_ast._asts[1])._asts[1] = AST.newASTFrame(f);
    Frame fr = Exec.execute(_ast).getFrame();
    _newCol = _inplace?f.names()[0]:f.uniquify(f.names()[0]);
    return fr;
  }

  @Override Frame inverseTransform(Frame f) { throw H2O.unimpl(); }

  public StringBuilder genClass(String stepName) {
    StringBuilder sb = new StringBuilder();
    String s = "public static class " + stepName + " extends Step<" + stepName + "> {\n" +
            "  private final String _col = " + _params.get("cols") + ";\n" +
            "  private final String _op  = " + _fun + ";\n" +
            "  private final String _newCol = " + _newCol + ";\n";
    sb.append(s);
    if( _params!=null ) {
      sb.append(
            "  private final HashMap<String, Object> _params = new HashMap<>();\n");

      for( String k: ((HashMap<String,Object>)_params).keySet() ) {
        Object o = _params.get(k);
        sb.append(
                "  _params.put(" + k + ", " + o +");\n" // TODO: o needs to be turned into proper String here.
        );
      }
    }
    String s3 =
            "  " + stepName + "() { _inplace = " + _inplace + "; } \n" +
            "  @Override public RowData transform(RowData row) {\n" +
            "    row.put(_newCol, GenModel.apply(_op, row.get(_col), _params);\n" +
            "    return row;\n" +
            "  }\n" +
            "}\n";
    sb.append(s3);
    return sb;
  }
}

