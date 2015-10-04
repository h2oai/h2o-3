package water.rapids.transforms;

import water.H2O;
import water.fvec.Frame;
import water.rapids.AST;
import water.rapids.ASTExec;

import java.util.HashMap;

public class H2OBinaryOp extends H2OColOp {
  boolean _leftIsCol;
  boolean _riteIsCol;
  String _binCol;  // !=null only if _leftIsCol || _riteIsCol
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

  public H2OBinaryOp(String name, String ast, boolean inplace, String[] newNames) {
    super(name, ast, inplace, newNames);
  }

  @Override protected void setupParamsImpl(int i, String[] args) {
    if( _ast._asts[i+1] instanceof ASTExec) {
      if( !isBinaryOp(_fun) ) throw H2O.unimpl("unimpl: " + lookup(_fun));
      _leftIsCol = args[i].equals("leftArg");
      _riteIsCol = !_leftIsCol;
      _binCol = ((ASTExec)_ast._asts[i+1])._asts[2].str();
      _params.put(args[i], AST.newASTStr(((ASTExec) _ast._asts[i + 1])._asts[2].str()));
    } else super.setupParamsImpl(i,args);
  }

  @Override protected Frame transformImpl(Frame f) {
    if( paramIsRow() ) ((ASTExec)_ast._asts[2])._asts[1] = AST.newASTFrame(f);
    return super.transformImpl(f);
  }

  @Override protected String lookup(String op) { return binaryOps.get(op)==null?op:binaryOps.get(op); }
  @Override protected boolean paramIsRow() { return _leftIsCol || _riteIsCol; }
  @Override protected String addRowParam() {
    return "      _params.put(\""+ (_leftIsCol?"leftArg":"rightArg") + "\", " +
            "new String[]{row.get(\"" +_binCol+ "\")}); // writer over the previous value\n";
  }
  private static boolean isBinaryOp(String op) { return binaryOps.get(op)!=null; }
}