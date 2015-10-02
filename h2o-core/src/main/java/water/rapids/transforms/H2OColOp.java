package water.rapids.transforms;

import org.apache.commons.lang.ArrayUtils;
import water.DKV;
import water.H2O;
import water.fvec.Frame;
import water.rapids.AST;
import water.rapids.ASTExec;
import water.rapids.ASTParameter;
import water.rapids.Exec;

import java.util.HashMap;

public class H2OColOp extends Transform<H2OColOp> {
  final String _fun;
  String _oldCol;
  String[] _newCol;
  boolean _multiColReturn;
  private static HashMap<String,String> binaryOps = new HashMap<>();

  static {
    binaryOps.put("+", "plus");
    binaryOps.put("-", "minus");
    binaryOps.put("*", "multiply");
    binaryOps.put("/", "divide");
    binaryOps.put("<", "lessThan");
    binaryOps.put("<=","lessThanEquals");
    binaryOps.put(">", "greaterThan");
    binaryOps.put(">=","greaterThanEquals");
    binaryOps.put("==", "equals");
    binaryOps.put("!=", "notEquals");
  }

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

  @Override public Transform<H2OColOp> fit(Frame f) { return this; }
  @Override protected Frame transformImpl(Frame f) {
    ((ASTExec)_ast._asts[1])._asts[1] = AST.newASTFrame(f);
    Frame fr = Exec.execute(_ast).getFrame();
    if( (_multiColReturn=fr.numCols() > 1) ) {
      _newCol = new String[fr.numCols()];
      for(int i=0;i<_newCol.length;i++)
        _newCol[i] = f.uniquify(i>0?_newCol[i-1]:_oldCol);
       throw H2O.unimpl("unimpl: multiple columns");
    } else {
      _newCol = new String[]{_inplace ? _oldCol : f.uniquify(_oldCol)};
      if( _inplace ) f.replace(f.find(_oldCol), fr.anyVec()).remove();
      else          f.add(_newCol[0], fr.anyVec());
    }
    DKV.put(f);
    return f;
  }

  @Override Frame inverseTransform(Frame f) { throw H2O.unimpl(); }

  @Override public String genClassImpl() {
    String typeCast = _inTypes[ArrayUtils.indexOf(_inNames, _oldCol)].equals("Numeric")?"double":"String";
    return  "    @Override public RowData transform(RowData row) {\n" +
            "      row.put(\""+_newCol+"\", GenMunger."+_fun+"(("+typeCast+")row.get(\""+_oldCol+"\"), _params));\n" +
            "      return row;\n" +
            "    }\n";
  }

  static String lookup(String op) {
    return binaryOps.get(op)==null?op:binaryOps.get(op);
  }
}
