package water.rapids.transforms;

import water.DKV;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.rapids.AST;
import water.rapids.ASTParameter;
import water.rapids.Exec;

public class H2OColSelect extends Transform<H2OColSelect> {
  private final String _cols;

  public H2OColSelect(String name, String ast, boolean inplace) {  // not a public constructor -- used by the REST api only; must be public for stupid java.lang.reflect
    super(name,ast,inplace);
    _cols = ((ASTParameter)_ast._asts[2]).toJavaString();
  }

  @Override public Transform<H2OColSelect> fit(Frame f) { return this; }
  @Override protected Frame transformImpl(Frame f) {
    _ast._asts[1] = AST.newASTFrame(f);
    Frame fr = Exec.execute(_ast).getFrame();
    if( fr._key==null ) fr = new Frame(Key.make("H2OColSelect_"+f._key.toString()),fr.names(),fr.vecs());
    DKV.put(fr);
    return fr;
  }
  @Override Frame inverseTransform(Frame f) { throw H2O.unimpl(); }
  public String genClassImpl() {
    return     "    private final String[] _cols = {"+ _cols +"};\n" +
               "    @Override public RowData transform(RowData row) {\n" +
               "      RowData colSelect = new RowData();\n" +
               "      for(String s: _cols) \n" +
               "        colSelect.put(s, row.get(s));\n" +
               "      return colSelect;\n"+
               "    }\n"+
               "  }\n";
  }
}
