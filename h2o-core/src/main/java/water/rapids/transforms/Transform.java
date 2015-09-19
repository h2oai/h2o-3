package water.rapids.transforms;

import water.Iced;
import water.fvec.Frame;
import water.rapids.ASTExec;
import water.rapids.ASTParameter;
import water.rapids.Exec;
import water.util.IcedHashMap;
import water.util.SB;

public abstract class Transform<T> extends Iced {
  protected final String _name;
  protected final ASTExec _ast;
  protected final boolean _inplace;
  protected final IcedHashMap<String,ASTParameter> _params;
  protected String[] _inNames;
  protected String[] _inTypes;

  Transform(String name, String ast, boolean inplace) {
    _name=name;
    _ast = (ASTExec)new Exec(ast).parse();
    _inplace = inplace;
    _params = new IcedHashMap<>();
  }
  public String name() { return _name; }
  public abstract Transform<T> fit(Frame f);
  public Frame transform(Frame f) {
    _inNames = f.names();
    _inTypes = f.typesStr();
    return transformImpl(f);
  }
  protected abstract Frame transformImpl(Frame f);
  abstract Frame inverseTransform(Frame f);
  public Frame fitTransform(Frame f) { return fit(f).transform(f); }
  public abstract String genClassImpl();
  public StringBuilder genClass() {
    String stepName = name();
    StringBuilder sb = new StringBuilder();
    sb.append("  class " + stepName + " extends Step<" + stepName + "> {\n");
    sb.append("    public " + stepName + "() { super(new String[]{" + toJavaString(_inNames) +"}, new String[]{" + toJavaString(_inTypes) + "}); }\n");
    for (String k : _params.keySet()) {
      String v = _params.get(k).toJavaString();
      sb.append(
              "    _params.put(\""+k+"\", new String[]{"+v+"});\n"
      );
    }
    return sb.append(genClassImpl()).append("  }\n");
  }

  private static String toJavaString(String[] strs) {
    if( strs==null || strs.length==0 ) return "\"null\"";
    SB sb = new SB();
    for(int i=0;i<strs.length;++i) {
      sb.p("\"").p(strs[i]).p("\"");
      if( i==strs.length-1) return sb.toString();
      sb.p(',');
    }
    throw new RuntimeException("Should never be here");
  }
}
