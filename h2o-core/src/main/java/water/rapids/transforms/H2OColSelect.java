package water.rapids.transforms;

import water.DKV;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.rapids.*;

public class H2OColSelect extends Transform<H2OColSelect> {
  private final String[] _cols;

  public H2OColSelect(String name, String ast, boolean inplace, String[] newNames) {  // not a public constructor -- used by the REST api only; must be public for stupid java.lang.reflect
    super(name,ast,inplace,newNames);
    ASTParameter cols = ((ASTParameter)_ast._asts[2]);
    if( cols instanceof ASTStrList ) _cols = ((ASTStrList)cols)._strs;
    else                             _cols = new String[]{cols._v.getStr()};
  }

  @Override public Transform<H2OColSelect> fit(Frame f) { return this; }
  @Override protected Frame transformImpl(Frame f) {
    _ast._asts[1] = AST.newASTFrame(f);
//    throw water.H2O.unimpl();
    Session ses = new Session();
    Frame fr = ses.exec(_ast,null).getFrame();
    if( fr._key==null ) fr = new Frame(Key.make("H2OColSelect_"+f._key.toString()),fr.names(),fr.vecs());
    DKV.put(fr);
    return fr;
  }
  @Override Frame inverseTransform(Frame f) { throw H2O.unimpl(); }
  public String genClassImpl() {
    StringBuilder sb = new StringBuilder();
    sb.append("    @Override public RowData transform(RowData row) {\n");
    sb.append("      RowData colSelect = new RowData();\n");
    for( String s: _cols)
      sb.append("      colSelect.put(\""+s+"\", row.get(\""+s+"\"));\n");
    sb.append("      return colSelect;\n").append("    }\n");
    return sb.toString();
  }
}
