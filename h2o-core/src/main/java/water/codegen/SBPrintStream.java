package water.codegen;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import water.util.IcedBitSet;

/**
 * A simple stream mimicking API of {@link SB}.
 */
public class SBPrintStream extends PrintStream implements JCodeSB<SBPrintStream> {

  private static final Package JAVA_LANG_PACKAGE = String.class.getPackage();

  private int _indent = 0;

  public SBPrintStream(OutputStream out) {
    super(out);
  }

  public SBPrintStream(OutputStream out, boolean autoFlush) {
    super(out, autoFlush);
  }

  public SBPrintStream(OutputStream out, boolean autoFlush, String encoding)
      throws UnsupportedEncodingException {
    super(out, autoFlush, encoding);
  }

  public SBPrintStream ps(String s) {
    append("\"");
    pj(s);
    append("\"");
    return this;
  }

  @Override
  public SBPrintStream p(JCodeSB s) {
    return p(s.getContent());
  }

  @Override
  public SBPrintStream p(String s) {
    append(s);
    return this;
  }

  @Override
  public SBPrintStream p(float s) {
    if (Float.isNaN(s)) {
      append("Float.NaN");
    } else if (Float.isInfinite(s)) {
      append(s > 0 ? "Float.POSITIVE_INFINITY" : "Float.NEGATIVE_INFINITY");
    } else {
      append(s);
    }
    return this;
  }

  @Override
  public SBPrintStream p(double s) {
    if (Double.isNaN(s)) {
      append("Double.NaN");
    } else if (Double.isInfinite(s)) {
      append(s > 0 ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY");
    } else {
      append(s);
    }
    return this;
  }

  @Override
  public SBPrintStream p(char s) {
    append(s);
    return this;
  }

  @Override
  public SBPrintStream p(int s) {
    append(s);
    return this;
  }

  @Override
  public SBPrintStream p(long s) {
    append(s);
    return this;
  }

  @Override
  public SBPrintStream p(boolean s) {
    append(Boolean.toString(s));
    return this;
  }

  // Not spelled "p" on purpose: too easy to accidentally say "p(1.0)" and
  // suddenly call the the autoboxed version.
  @Override
  public SBPrintStream pobj(Object s) {
    append(s.toString());
    return this;
  }

  @Override
  public SBPrintStream p(Enum e) {
    append(e.toString());
    return this;
  }

  @Override
  public SBPrintStream i(int d) {
    for (int i = 0; i < d + _indent; i++) {
      p("  ");
    }
    return this;
  }

  @Override
  public SBPrintStream i() {
    return i(0);
  }

  @Override
  public SBPrintStream ip(String s) {
    return i().p(s);
  }

  @Override
  public SBPrintStream s() {
    append(' ');
    return this;
  }

  // Java specific append of double
  @Override
  public SBPrintStream pj(double s) {
    if (Double.isInfinite(s)) {
      append("Double.").append(s > 0 ? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY");
    } else if (Double.isNaN(s)) {
      append("Double.NaN");
    } else {
      append(s);
    }
    return this;
  }

  // Java specific append of float
  @Override
  public SBPrintStream pj(float s) {
    if (Float.isInfinite(s)) {
      append("Float.").append(s > 0 ? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY");
    } else if (Float.isNaN(s)) {
      append("Float.NaN");
    } else {
      append(s).append('f');
    }
    return this;
  }

  /* Append Java string - escape all " and \ */
  @Override
  public SBPrintStream pj(String s) {
    append('"').append(SB.escapeJava(s)).append('"');
    return this;
  }

  @Override
  public SBPrintStream pj(Class c) {
    Package p = c.getPackage();
    append(p != null && p == JAVA_LANG_PACKAGE ? c.getSimpleName() : c.getCanonicalName());
    return this;
  }

  @Override
  public SBPrintStream pj( long l ) {
    append(l).append('L');
    return this;
  }

  @Override
  public SBPrintStream pj(double[] ary) {
    return toJavaStringInit(ary);
  }

  @Override
  public SBPrintStream pbraces(int dim) {
    while (dim-- > 0) {
      append("[]");
    }
    return this;
  }

  @Override
  public SBPrintStream lineComment(String s) {
    return p("// ").p(s);
  }

  @Override
  public SBPrintStream blockComment(String s) {
    return p("/* ").p(s).p(" */");
  }

  @Override
  public SBPrintStream pj(String objectName, String fieldName) {
    append(objectName).append('.').append(fieldName);
    return this;
  }

  @Override
  public SBPrintStream p(IcedBitSet ibs) {
    SB sb = new SB();
    sb = ibs.toString(sb);
    return append(sb);
  }

  public SBPrintStream p(SB sb) {
    return append(sb);
  }

  // Increase indentation
  @Override
  public SBPrintStream ii(int i) {
    _indent += i;
    return this;
  }

  // Decrease indentation
  @Override
  public SBPrintStream di(int i) {
    _indent -= i;
    return this;
  }

  // Copy indent from given string buffer
  @Override
  public SBPrintStream ci(JCodeSB sb) {
    _indent = sb.getIndent();
    return this;
  }

  @Override
  public SBPrintStream nl() {
    return p('\n').i();
  }

  @Override
  public SBPrintStream nl(int n) {
    while (n-- > 0) nl();
    return this;
  }

  // Convert a String[] into a valid Java String initializer
  @Override
  public SBPrintStream toJavaStringInit(String[] ss) {
    if (ss == null) {
      return p("null");
    }
    p('{');
    for (int i = 0; i < ss.length - 1; i++) {
      p('"').pj(ss[i]).p("\",");
    }
    if (ss.length > 0) {
      p('"').pj(ss[ss.length - 1]).p('"');
    }
    return p('}');
  }

  @Override
  public SBPrintStream toJavaStringInit(float[] ss) {
    if (ss == null) {
      return p("null");
    }
    p('{');
    for (int i = 0; i < ss.length - 1; i++) {
      pj(ss[i]).p(',');
    }
    if (ss.length > 0) {
      pj(ss[ss.length - 1]);
    }
    return p('}');
  }

  @Override
  public SBPrintStream toJavaStringInit(double[] ss) {
    if (ss == null) {
      return p("null");
    }
    p('{');
    for (int i = 0; i < ss.length - 1; i++) {
      pj(ss[i]).p(',');
    }
    if (ss.length > 0) {
      pj(ss[ss.length - 1]);
    }
    return p('}');
  }

  @Override
  public SBPrintStream toJavaStringInit(double[][] ss) {
    if (ss == null) {
      return p("null");
    }
    p('{');
    for (int i = 0; i < ss.length - 1; i++) {
      toJavaStringInit(ss[i]).p(',');
    }
    if (ss.length > 0) {
      toJavaStringInit(ss[ss.length - 1]);
    }
    return p('}');
  }

  @Override
  public SBPrintStream toJavaStringInit(double[][][] ss) {
    if (ss == null) {
      return p("null");
    }
    p('{');
    for (int i = 0; i < ss.length - 1; i++) {
      toJavaStringInit(ss[i]).p(',');
    }
    if (ss.length > 0) {
      toJavaStringInit(ss[ss.length - 1]);
    }
    return p('}');
  }

  @Override
  public SBPrintStream toJSArray(float[] nums) {
    p('[');
    for (int i = 0; i < nums.length; i++) {
      if (i > 0) {
        p(',');
      }
      p(nums[i]);
    }
    return p(']');
  }

  @Override
  public SBPrintStream toJSArray(String[] ss) {
    p('[');
    for (int i = 0; i < ss.length; i++) {
      if (i > 0) {
        p(',');
      }
      p('"').p(ss[i]).p('"');
    }
    return p(']');
  }

  @Override
  public int getIndent() {
    return _indent;
  }

  @Override
  public String getContent() {
    throw new UnsupportedOperationException("Cannot get content of stream!");
  }

  //
  // Copied from AbstractStringBuilder
  // FIXME: optimize that
  //
  public SBPrintStream append(float f) {
    append(Float.toString(f));
    return this;
  }

  public SBPrintStream append(double d) {
    append(Double.toString(d));
    return this;
  }

  public SBPrintStream append(int i) {
    append(Integer.toString(i));
    return this;
  }

  public SBPrintStream append(long l) {
    append(Long.toString(l));
    return this;
  }

  public SBPrintStream append(SB sb) {
    append(sb.toString());
    return this;
  }
}


