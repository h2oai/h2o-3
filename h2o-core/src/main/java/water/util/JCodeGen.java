package water.util;

/** Internal utility for pretty-printing Models as Java code
 */
public class JCodeGen {

  public static SB toStaticVar(SB sb, String varname, int value, String comment) {
    if (comment!=null) sb.ip("// ").p(comment).nl();
    return sb.ip("public static final int ").p(varname).p(" = ").p(value).p(';').nl();
  }

  public static SB toStaticVar(SB sb, String varname, String[] values, String comment) {
    if (comment!=null) sb.ip("// ").p(comment).nl();
    sb.ip("public static final String[] ").p(varname).p(" = ");
    if (values == null) return sb.p("null;").nl();
    sb.p("new String[]{").p("\""+values[0]+"\"");
    for (int i = 1; i < values.length; ++i) sb.p(",").p("\""+values[i]+"\"");
    return sb.p("};").nl();
  }

  public static SB toStaticVar(SB sb, String varname, float[] values, String comment) {
    if (comment!=null) sb.ip("// ").p(comment).nl();
    sb.ip("public static final float[] ").p(varname).p(" = ");
    if (values == null) return sb.p("null;").nl();
    sb.p("{").pj(values[0]);
    for (int i = 1; i < values.length; ++i) sb.p(",").pj(values[i]);
    return sb.p("};").nl();
  }

  /**
   * Generates a new class with one static member called <em>VALUES</em> which
   * is filled by values of given array.
   * <p>The generator can generate more classes to avoid limit of class constant
   * pool holding all generated literals</p>.
   *
   * @param sb output
   * @param className name of generated class
   * @param values array holding values which should be hold in generated field VALUES.
   * @return output buffer
   */
  public static SB toClassWithArray(SB sb, String modifiers, String className, String[] values) {
    sb.ip(modifiers!=null ? modifiers+" ": "").p("class ").p(className).p(" {").nl().ii(1);
    sb.ip("public static final String[] VALUES = ");
    if (values==null)
      sb.p("null;").nl();
    else {
      sb.p("new String[").p(values.length).p("];").nl();

      // Static part
      int s = 0;
      int remain = values.length;
      int its = 0;
      SB sb4fillers = new SB().ci(sb);
      sb.ip("static {").ii(1).nl();
      while (remain>0) {
          String subClzName = className + "_" + its++;
          int len = Math.min(MAX_STRINGS_IN_CONST_POOL, remain);
          toClassWithArrayFill(sb4fillers, subClzName, values, s, len);
          sb.ip(subClzName).p(".fill(VALUES);").nl();
          s += len;
          remain -= len;
      }
      sb.di(1).ip("}").nl();
      sb.p(sb4fillers);
    }
    return sb.di(1).p("}").nl();
  }

  /** Maximum number of string generated per class (static initializer) */
  public static int MAX_STRINGS_IN_CONST_POOL = 3000;

  public static SB toClassWithArrayFill(SB sb, String clzName, String[] values, int start, int len) {
    sb.ip("static final class ").p(clzName).p(" {").ii(1).nl();
    sb.ip("static final void fill(String[] sa) {").ii(1).nl();
    for (int i=0; i<len; i++) {
      sb.ip("sa[").p(start+i).p("] = ").ps(values[start+i]).p(";").nl();
    }
    sb.di(1).ip("}").nl();
    sb.di(1).ip("}").nl();
    return sb;
  }

  /** Transform given string to legal java Identifier (see Java grammar http://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.8) */
  public static String toJavaId(String s) {
    // Note that the leading 4 backslashes turn into 2 backslashes in the
    // string - which turn into a single backslash in the REGEXP.
    // "+-*/ !@#$%^&()={}[]|\\;:'\"<>,.?/"
    return s.replaceAll("[+\\-* !@#$%^&()={}\\[\\]|;:'\"<>,.?/]",  "_");
  }
}
