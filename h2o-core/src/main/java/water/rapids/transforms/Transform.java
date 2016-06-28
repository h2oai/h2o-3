package water.rapids.transforms;

import water.Iced;
import water.fvec.Frame;
import water.rapids.ASTExec;
import water.rapids.ASTParameter;
import water.rapids.Rapids;
import water.util.IcedHashMap;
import water.util.SB;

public abstract class Transform<T> extends Iced {
  protected final String _name;
  protected final ASTExec _ast;
  protected final boolean _inplace;
  protected final String[] _newNames;
  protected final IcedHashMap<String,ASTParameter> _params;
  protected String[] _inNames;
  protected String[] _inTypes;
  protected String[] _outTypes;
  protected String[] _outNames;

  Transform(String name, String ast, boolean inplace, String[] newNames) {
    _name=name;
    _ast = (ASTExec) Rapids.parse(ast);
    _inplace = inplace;
    _newNames = newNames;
    _params = new IcedHashMap<>();
  }
  public String name() { return _name; }
  protected abstract Transform<T> fit(Frame f);
  public Frame transform(Frame f) {
    _inNames = f.names();
    _inTypes = f.typesStr();
    Frame ff = transformImpl(f);
    _outTypes= ff.typesStr();
    _outNames= ff.names();
    return ff;
  }
  protected abstract Frame transformImpl(Frame f);
  abstract Frame inverseTransform(Frame f);
  public Frame fitTransform(Frame f) { return fit(f).transform(f); }
  public abstract String genClassImpl();
  public StringBuilder genClass() {
    String stepName = name();
    StringBuilder sb = new StringBuilder();
    sb.append("  class " + stepName + " extends Step<" + stepName + "> {\n");
    sb.append("    public " + stepName + "() { super(new String[]{" + toJavaString(_inNames) +"},\n");
    sb.append("                                new String[]{" + toJavaString(_inTypes) + "}," +
              "                                new String[]{" + toJavaString(_outNames) +"});\n");
    for (String k : _params.keySet()) {
      String v = _params.get(k).toJavaString();
      sb.append(
              "    _params.put(\""+k+"\", new String[]{"+v.replace("\\","\\\\")+"});\n"
      );
    }
    sb.append("  }\n");
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

  protected static String toJavaPrimitive(String vecType) {
    if( vecType.equals("String") || vecType.equals("Enum") ) return "String";
    return "double";
  }
}
