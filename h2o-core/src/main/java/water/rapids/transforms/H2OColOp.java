package water.rapids.transforms;

import water.DKV;
import water.H2O;
import water.rapids.AST;
import water.rapids.ASTExec;
import water.rapids.ASTParameter;
import water.rapids.Exec;
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
    String s = "  class " + stepName + " extends Step<" + stepName + "> {\n";
    sb.append(s);
    if( _params.size() > 0 )
      sb.append(
               "    private final HashMap<String, String[]> _params = new HashMap<>();\n");

    for( String k: _params.keySet() ) {
      String v = _params.get(k).toJavaString();
      sb.append(
               "    _params.put(\""+k+"\", new String[]{"+v+"});\n"
      );
    }
    String s3 =
               "    public " + stepName + "() { } \n" +
                    "    @Override public RowData transform(RowData row) {\n" +
                    "      row.put(\""+_newCol+"\", GenModel."+_fun+"(row.get(\""+_oldCol+"\"), _params);\n" +
                    "      return row;\n" +
                    "    }\n" +
                    "  }\n";
    sb.append(s3);
    return sb;
  }
}
