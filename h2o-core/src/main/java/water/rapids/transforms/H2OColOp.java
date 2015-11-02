package water.rapids.transforms;

import org.apache.commons.lang.ArrayUtils;
import water.DKV;
import water.H2O;
import water.fvec.Frame;
import water.rapids.*;

public class H2OColOp extends Transform<H2OColOp> {
  protected final String _fun;
  private final String _oldCol;
  private String[] _newCol;
  private String _newColTypes;
  boolean _multiColReturn;

  public H2OColOp(String name, String ast, boolean inplace, String[] newNames) { // (op (cols fr cols) {extra_args})
    super(name,ast,inplace,newNames);
    _fun = _ast._asts[0].str();
    _oldCol = ((ASTExec)_ast._asts[1])._asts[2].str();
    setupParams();
  }

  private void setupParams() {
    String[] args = _ast.getArgs();
    if( args!=null && args.length > 1 ) { // first arg is the frame
      for(int i=1; i<args.length; ++i)
        setupParamsImpl(i,args);
    }
  }

  protected void setupParamsImpl(int i, String[] args) {
    _params.put(args[i], (ASTParameter) _ast._asts[i + 1]);
  }

  @Override public Transform<H2OColOp> fit(Frame f) { return this; }
  @Override protected Frame transformImpl(Frame f) {
    ((ASTExec)_ast._asts[1])._asts[1] = AST.newASTFrame(f);
    Session ses = new Session();
    Frame fr = ses.exec(_ast, null).getFrame();
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
      else          f.add(_newNames == null ? _newCol[0] : _newNames[0], fr.anyVec());
    }
    DKV.put(f);
    return f;
  }

  @Override Frame inverseTransform(Frame f) { throw H2O.unimpl(); }

  @Override public String genClassImpl() {
    String typeCast = _inTypes[ArrayUtils.indexOf(_inNames, _oldCol)].equals("Numeric")?"double":"String";

    if( _multiColReturn ) {
      StringBuilder sb = new StringBuilder(
              "    @Override public RowData transform(RowData row) {\n"+
              (paramIsRow() ? addRowParam() : "") +
              "     "+_newColTypes+"[] res = GenMunger."+lookup(_fun)+"(("+typeCast+")row.get(\""+_oldCol+"\"), _params);\n");
      for(int i=0;i<_newCol.length;i++)
        sb.append(
              "      row.put(\""+_newCol[i]+"\", res["+i+"]);\n");
      sb.append(
              "      return row;\n" +
              "    }\n");
      return sb.toString();
    } else {
      return "    @Override public RowData transform(RowData row) {\n"+
             (paramIsRow() ? addRowParam() : "") +
             "      "+_newColTypes+" res = GenMunger."+lookup(_fun)+"(("+typeCast+")row.get(\""+_oldCol+"\"), _params);\n"+
             "      row.put(\""+_newCol[0]+"\", res);\n" +
             "      return row;\n" +
             "    }\n";
    }
  }

  protected boolean paramIsRow() { return false; }
  protected String addRowParam() { return ""; }
  protected String lookup(String op) { return op.replaceAll("\\.",""); }
}
