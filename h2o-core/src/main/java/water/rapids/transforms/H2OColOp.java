package water.rapids.transforms;

import org.apache.commons.lang.ArrayUtils;
import water.DKV;
import water.H2O;
import water.fvec.Frame;
import water.rapids.*;
import water.rapids.ast.AstExec;
import water.rapids.ast.AstParameter;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstId;
import water.rapids.ast.prims.mungers.AstColPySlice;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class H2OColOp extends Transform<H2OColOp> {

  private static final String FRAME_ID_PLACEHOLDER = "dummy";

  protected final String _fun;
  protected String _oldCol;
  private String[] _newCol;
  private String _newJavaColTypes;
  private String _newColTypes;
  boolean _multiColReturn;

  @Override
  public String[] getNewNames() { return _newCol; }
  @Override
  public String[] getNewTypes() { 
    String[] result = new String[_newCol.length == 0 ? 1 : _newCol.length]; 
    Arrays.fill(result, _newColTypes);
    return result;
  }
  
  public String[] getOldNames() { return new String[]{_oldCol}; }


  public H2OColOp(String name, String ast, boolean inplace, String[] newNames) { // (op (cols fr cols) {extra_args})
    super(name,ast,inplace,newNames);
    _fun = _ast._asts[0].str();
    _oldCol = null;
    for(int i=1; i<_ast._asts.length; ++i) {
      if (_ast._asts[i] instanceof AstExec) {
        _oldCol = findOldName((AstExec)_ast._asts[i]);
        break;
      }
    }
    setupParams();
  }

  private void setupParams() {
    String[] args = _ast.getArgs();
    if( args!=null && args.length > 1 ) { // first arg is the frame
      for(int i=0; i < args.length; ++i)
        setupParamsImpl(i,args);
    }
  }
  

  protected void setupParamsImpl(int i, String[] args) {
    if (_ast._asts[i + 1] instanceof AstParameter) {
      _params.put(args[i], (AstParameter) _ast._asts[i + 1]);
    }
  }

  @Override public Transform<H2OColOp> fit(Frame f) { return this; }
  @Override protected Frame transformImpl(Frame f) {
    substitutePlaceholders(_ast, f);

    Session ses = new Session();
    Frame fr = ses.exec(_ast, null).getFrame();
    _newCol = _newNames==null?new String[fr.numCols()]:_newNames;
    _newColTypes = fr.anyVec().get_type_str();
    _newJavaColTypes = toJavaPrimitive(_newColTypes);
    if( (_multiColReturn=fr.numCols() > 1) ) {
      for(int i=0;i<_newCol.length;i++) {
        if(_newNames==null) _newCol[i] = f.uniquify(i > 0 ? _newCol[i - 1] : _oldCol);
        f.add(_newCol[i], fr.vec(i));
      }
      if( _inplace ) f.remove(f.find(_oldCol)).remove();
    } else {
      _newCol = _newNames==null?new String[]{_inplace ? _oldCol : f.uniquify(_oldCol)}:_newCol;
      if( _inplace ) f.replace(f.find(_oldCol), fr.anyVec()).remove();
      else          f.add(_newNames == null ? _newCol[0] : _newNames[0], fr.anyVec());
    }
    DKV.put(f);
    return f;
  }

  private void substitutePlaceholders(AstExec root, Frame f) {
    Queue<AstExec> execs = new LinkedList<>();
    execs.add(root);
    while (! execs.isEmpty()) {
      AstExec exec = execs.poll();
      for (int i = 1; i < exec._asts.length; i++) {
        AstRoot<?> ast = exec._asts[i];
        if (ast instanceof AstExec)
          execs.add((AstExec) ast);
        else if (ast instanceof AstId) {
          AstId id = (AstId) ast;
          if (FRAME_ID_PLACEHOLDER.equals(id.str()))
            exec._asts[i] = new AstId(f);
        }
      }
    }
  }

  private static String findOldName(AstExec root) {
    AstColPySlice py = new AstColPySlice();
    Queue<AstExec> execs = new LinkedList<>();
    execs.add(root);
    String oldName = null;
    while (! execs.isEmpty()) {
      AstExec exec = execs.poll();
      // (cols_py dummy <oldName>)
      if (exec._asts.length == 3 && py.str().equals(exec._asts[0].str()) && FRAME_ID_PLACEHOLDER.equals(exec._asts[1].str())) {
        oldName = exec._asts[2].str();
        break;
      }
      for (int i = 1; i < exec._asts.length; i++) {
        AstRoot<?> ast = exec._asts[i];
        if (ast instanceof AstExec)
          execs.add((AstExec) ast);
      }
    }
    return oldName;
  }

  @Override Frame inverseTransform(Frame f) { throw H2O.unimpl(); }

  @Override public String genClassImpl() {
    final int typeId = ArrayUtils.indexOf(_inNames, _oldCol);
    if (typeId < 0)
      throw new IllegalStateException("Unknown column " + _oldCol + " (known: " + Arrays.toString(_inNames));
    String typeCast = _inTypes[typeId].equals("Numeric")?"Double":"String";

    if( _multiColReturn ) {
      StringBuilder sb = new StringBuilder(
              "    @Override public RowData transform(RowData row) {\n"+
              (paramIsRow() ? addRowParam() : "") +
              "     "+_newJavaColTypes+"[] res = GenMunger."+lookup(_fun)+"(("+typeCast+")row.get(\""+_oldCol+"\"), _params);\n");
      for(int i=0;i<_newCol.length;i++)
        sb.append(
              "      row.put(\""+_newCol[i]+"\",("+i+">=res.length)?\"\":res["+i+"]);\n");
      sb.append(
              "      return row;\n" +
              "    }\n");
      return sb.toString();
    } else {
      return "    @Override public RowData transform(RowData row) {\n"+
             (paramIsRow() ? addRowParam() : "") +
             "      "+_newJavaColTypes+" res = GenMunger."+lookup(_fun)+"(("+typeCast+")row.get(\""+_oldCol+"\"), _params);\n"+
             "      row.put(\""+_newCol[0]+"\", res);\n" +
             "      return row;\n" +
             "    }\n";
    }
  }

  protected boolean paramIsRow() { return false; }
  protected String addRowParam() { return ""; }
  protected String lookup(String op) { return op.replaceAll("\\.",""); }
}
