package water.rapids.transforms;

import water.H2O;
import water.rapids.ast.AstExec;
import water.rapids.ast.params.AstStr;

import java.util.HashMap;

@SuppressWarnings("unused")  // called thru reflection
public class H2OBinaryOp extends H2OColOp {
  boolean _leftIsCol;
  boolean _riteIsCol;
  
  String _binCol;  // !=null only if _leftIsCol || _riteIsCol
  
  public boolean getIsLeftColumn() {
    return _leftIsCol;
  }
  
  public boolean getIsRightColumn() {
    return _riteIsCol;
  }

  @Override
  public String[] getOldNames() { return _binCol == null ? new String[]{_oldCol} : new String[]{_oldCol, _binCol}; }
  
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
    binaryOps.put("^", "pow");
    binaryOps.put("%", "mod");
    binaryOps.put("%%", "mod");
    binaryOps.put("&", "and");
    binaryOps.put("&&", "and");
    binaryOps.put("|", "or");
    binaryOps.put("||", "or");
    binaryOps.put("intDiv", "intDiv");
    binaryOps.put("strDistance", "strDistance");
  }

  public H2OBinaryOp(String name, String ast, boolean inplace, String[] newNames) {
    super(name, ast, inplace, newNames);
  }

  @Override protected void setupParamsImpl(int i, String[] args) {
    if( _ast._asts[i+1] instanceof AstExec) {
      if( !isBinaryOp(_fun) ) throw H2O.unimpl("unimpl: " + lookup(_fun));
      if (args[i].equals("leftArg") || args[i].equals("ary_x")) {
        _leftIsCol = true;
      } else if (args[i].equals("rightArg") || args[i].equals("ary_y")) {
        _riteIsCol = true;
      }
      _binCol = ((AstExec)_ast._asts[i+1])._asts[2].str();
      _params.put(args[i], new AstStr(((AstExec) _ast._asts[i + 1])._asts[2].str()));
    } else super.setupParamsImpl(i,args);
  }

  @Override protected String lookup(String op) { return binaryOps.get(op)==null?op:binaryOps.get(op); }
  @Override protected boolean paramIsRow() { return _leftIsCol || _riteIsCol; }
  @Override protected String addRowParam() {
    return "      _params.put(\""+ (_riteIsCol?"rightArg":"leftArg") + "\", " +
            "new String[]{String.valueOf(row.get(\"" +_binCol+ "\"))}); // write over the previous value\n";
  }
  private static boolean isBinaryOp(String op) { return binaryOps.get(op)!=null; }
}
