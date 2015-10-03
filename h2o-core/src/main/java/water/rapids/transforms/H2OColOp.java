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
  private final String _fun;
  String _oldCol;
  String[] _newCol;
  String _newColTypes;
  boolean _multiColReturn;
  boolean _leftIsCol;
  boolean _riteIsCol;
  String _binCol;  // only if _leftIsCol || _riteIsCol
  private static final HashMap<String,String> binaryOps = new HashMap<>();

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

  public H2OColOp(String name, String ast, boolean inplace, String[] newNames) { // (op (cols fr cols) {extra_args})
    super(name,ast,inplace,newNames);
    _fun = _ast._asts[0].str();
    _oldCol = ((ASTExec)_ast._asts[1])._asts[2].str();
    String[] args = _ast.getArgs();
    if( args!=null && args.length > 1 ) { // first arg is the frame
      for(int i=1; i<args.length; ++i) {
        if( _ast._asts[i+1] instanceof ASTExec) {
          if( !isBinaryOp(_fun) ) throw H2O.unimpl("unimpl: " + lookup(_fun));
          _leftIsCol = args[i].equals("leftArg");
          _riteIsCol = !_leftIsCol;
          _binCol = ((ASTExec)_ast._asts[i+1])._asts[2].str();
          _params.put(args[i], AST.newASTStr(((ASTExec)_ast._asts[i+1])._asts[2].str()));
        } else
          _params.put(args[i], (ASTParameter) _ast._asts[i + 1]);
      }
    }
  }

  @Override public Transform<H2OColOp> fit(Frame f) { return this; }
  @Override protected Frame transformImpl(Frame f) {
    ((ASTExec)_ast._asts[1])._asts[1] = AST.newASTFrame(f);
    if( _leftIsCol || _riteIsCol )
      ((ASTExec)_ast._asts[2])._asts[1] = AST.newASTFrame(f);
    Frame fr = Exec.execute(_ast).getFrame();
    _newCol = _newNames==null?new String[fr.numCols()]:_newNames;
    _newColTypes = toJavaPrimitive(fr.anyVec().get_type_str());
    if( (_multiColReturn=fr.numCols() > 1) ) {
      for(int i=0;i<_newCol.length;i++) {
        if(_newNames==null) _newCol[i] = f.uniquify(i > 0 ? _newCol[i - 1] : _oldCol);
        f.add(_newCol[i], fr.vec(i));
      }
      if( _inplace ) f.remove(f.find(_oldCol)).remove();
    } else {
      _newCol = new String[]{_inplace ? _oldCol : f.uniquify(_oldCol)};
      if( _inplace ) f.replace(f.find(_oldCol), fr.anyVec()).remove();
      else          f.add(_newNames==null?_newCol[0]:_newNames[0], fr.anyVec());
    }
    DKV.put(f);
    return f;
  }

  @Override Frame inverseTransform(Frame f) { throw H2O.unimpl(); }

  @Override public String genClassImpl() {
    String typeCast = _inTypes[ArrayUtils.indexOf(_inNames, _oldCol)].equals("Numeric")?"double":"String";
    StringBuilder sb =   new StringBuilder(
            "    @Override public RowData transform(RowData row) {\n");

    if( _leftIsCol || _riteIsCol ) // need to fetch left/right value for binary op from (RowData)row;
      sb.append("      _params.put(\""+ (_leftIsCol?"leftArg":"rightArg") +"\", new String[]{row.get(\"" +_binCol+ "\")}; // writer over the previous value");

    sb.append("      " + _newColTypes + (_multiColReturn?"[]":"") + " res = GenMunger."+lookup(_fun)+"(("+typeCast+")row.get(\""+_oldCol+"\"), _params));\n");

    for(int i=0; i<_newCol.length; ++i) {
      sb.append(
            "      row.put(\""+_newCol[i]+"\", res" + (_multiColReturn?"["+i+"]":"") + ");\n"
      );
    }
    sb.append(
            "      return row;\n" +
            "    }\n"
    );
    return sb.toString();
  }

  static String lookup(String op) { return binaryOps.get(op)==null?op:binaryOps.get(op); }
  static boolean isBinaryOp(String op) { return binaryOps.get(op)!=null; }
}
