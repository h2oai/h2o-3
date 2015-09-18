package water.currents.transforms;

import water.DKV;
import water.H2O;
import water.currents.AST;
import water.currents.ASTExec;
import water.currents.ASTParameter;
import water.currents.Exec;
import water.fvec.Frame;

public class H2OColOp extends Transform<H2OColOp> {
  final String _fun;
  String _oldCol;
  String _newCol;

  public H2OColOp(String name, String ast, boolean inplace) { // (op (cols fr cols) {extra_args})
    super(name,ast,inplace);
    _fun = _ast._asts[0].str();
    _oldCol = ((ASTExec)_ast._asts[1])._asts[2].str();
    String[] args = _ast.getArgs();
    if( args!=null && args.length > 1 ) { // first arg is the frame
      for(int i=1; i<args.length; ++i)
        _params.put(args[i],(ASTParameter)_ast._asts[i+1]);
    }
  }

  @Override Transform<H2OColOp> fit(Frame f) { return this; }

  @Override Frame transform(Frame f) {
    ((ASTExec)_ast._asts[1])._asts[1] = AST.newASTFrame(f);
    Frame fr = Exec.execute(_ast).getFrame();
    _newCol = _inplace?_oldCol:f.uniquify(_oldCol);
    if( _inplace ) f.replace(f.find(_oldCol), fr.anyVec()).remove();
    else           f.add(_newCol,fr.anyVec());
    DKV.put(f);
    return f;
  }

  @Override Frame inverseTransform(Frame f) { throw H2O.unimpl(); }

  @Override public StringBuilder genClass() {
    String stepName = name();
    StringBuilder sb = new StringBuilder();
    String s = "public static class " + stepName + " extends Step<" + stepName + "> {\n" +
            "  private final String _col = " + _params.get("cols") + ";\n" +
            "  private final String _op  = " + _fun + ";\n" +
            "  private final String _newCol = " + _newCol + ";\n";
    sb.append(s);
    sb.append(
            "  private final HashMap<String, Object> _params = new HashMap<>();\n");

    for( String k: _params.keySet() ) {
      ASTParameter o = _params.get(k);
      sb.append(
              "  _params.put(" + k + ", " + o +");\n" // TODO: o needs to be turned into proper String here.
      );
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