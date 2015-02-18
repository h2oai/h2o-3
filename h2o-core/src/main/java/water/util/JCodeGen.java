package water.util;

import water.fvec.Frame;
import water.fvec.Vec;

/** Internal utility for pretty-printing Models as Java code
 */
public class JCodeGen {

  /** Generates data sample as a dedicated class with static <code>double[][]</code> member. */
  public static SB toClass(SB sb, String classSig, String varname, Frame f, int nrows, String comment) {
    sb.p(classSig).p(" {").nl().ii(1);
    toStaticVar(sb, varname, f, nrows, comment).di(1);
    return sb.p("}").nl();
  }

  /**
   * Outputs given frame as static variable with given name.
   */
  public static SB toStaticVar(SB sb, String varname, Frame f, int nrows, String comment) {
    if (comment!=null) sb.i(1).p("// ").p(comment).nl();
    sb.i(1).p("public static final double[][] ").p(varname).p(" = new double[][] {").nl();
    if (f!=null) {
      Vec[] vecs = f.vecs();
      for( int row = 0; row < Math.min(nrows,f.numRows()); row++ ) {
        sb.i(2).p(row > 0 ? "," : "").p("new double[] {");
        for( int v = 0; v < vecs.length; v++ )
          sb.p(v > 0 ? "," : "").p(vecs[v].at(row));
        sb.p("}").nl();
      }
    }
    sb.i(1).p("};").nl();
    return sb;
  }

  public static SB toStaticVar(SB sb, String varname, int value) {
    return toStaticVar(sb, varname, value, null);
  }
  public static SB toStaticVar(SB sb, String varname, int value, String comment) {
    if (comment!=null) sb.i(1).p("// ").p(comment).nl();
    return sb.i(1).p("public static final int ").p(varname).p(" = ").p(value).p(';').nl();
  }

  public static SB toStaticVar(SB sb, String varname, String[] values) {
    return toStaticVar(sb, varname, values, null);
  }
  public static SB toStaticVar(SB sb, String varname, String[] values, String comment) {
    if (comment!=null) sb.i(1).p("// ").p(comment).nl();
    sb.i(1).p("public static final String[] ").p(varname).p(" = ");
    if (values == null) return sb.p("null;").nl();
    sb.p("{").p("\""+values[0]+"\"");
    for (int i = 1; i < values.length; ++i) sb.p(",").p("\""+values[i]+"\"");
    return sb.p("};").nl();
  }
  public static SB toStaticVar(SB sb, String varname, int[] values) {
    return toStaticVar(sb, varname, values, null);
  }
  public static SB toStaticVar(SB sb, String varname, int[] values, String comment) {
    if (comment!=null) sb.i(1).p("// ").p(comment).nl();
    sb.i(1).p("public static final int[] ").p(varname).p(" = ");
    if (values == null || values.length == 0) return sb.p("null;").nl();
    sb.p("{").p(values[0]);
    for (int i = 1; i < values.length; ++i) sb.p(",").p(values[i]);
    return sb.p("};").nl();
  }
  public static SB toStaticVar(SB sb, String varname, float[] values) {
    return toStaticVar(sb, varname, values, null);
  }
  public static SB toStaticVar(SB sb, String varname, float[] values, String comment) {
    if (comment!=null) sb.i(1).p("// ").p(comment).nl();
    sb.i(1).p("public static final float[] ").p(varname).p(" = ");
    if (values == null) return sb.p("null;").nl();
    sb.p("{").pj(values[0]);
    for (int i = 1; i < values.length; ++i) sb.p(",").pj(values[i]);
    return sb.p("};").nl();
  }
  public static SB toStaticVar(SB sb, String varname, double[] values) {
    return toStaticVar(sb, varname, values, null);
  }
  public static SB toStaticVar(SB sb, String varname, double[] values, String comment) {
    if (comment!=null) sb.i(1).p("// ").p(comment).nl();
    sb.i(1).p("public static final double[] ").p(varname).p(" = ");
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
    sb.i().p(modifiers!=null ? modifiers+" ": "").p("class ").p(className).p(" {").nl().ii(1);
    sb.i().p("public static final String[] VALUES = ");
    if (values==null)
      sb.p("null;").nl();
    else {
      sb.p("new String[").p(values.length).p("];").nl();

      // Static part
      int s = 0;
      int remain = values.length;
      int its = 0;
      SB sb4fillers = new SB().ci(sb);
      sb.i().p("static {").ii(1).nl();
      while (remain>0) {
          String subClzName = className + "_" + its++;
          int len = Math.min(MAX_STRINGS_IN_CONST_POOL, remain);
          toClassWithArrayFill(sb4fillers, subClzName, values, s, len);
          sb.i().p(subClzName).p(".fill(VALUES);").nl();
          s += len;
          remain -= len;
      }
      sb.di(1).i().p("}").nl();
      sb.p(sb4fillers);
    }
    return sb.di(1).p("}").nl();
  }

  /**
   *
   * @param sb
   * @param className
   * @param values
   * @return
   */
  public static SB toClassWithArray(SB sb, String modifiers, String className, float[] values) {
    sb.i().p(modifiers != null ? modifiers + " " : "").p("class ").p(className).p(" {").nl().ii(1);
    sb.i().p("public static final float[] VALUES = ");
    if (values == null) {
      sb.p("null;").nl();
    } else {
      sb.p("new float[").p(values.length).p("];").nl();
      // Static part
      int s = 0;
      int remain = values.length;
      int its = 0;
      SB sb4fillers = new SB().ci(sb);
      sb.i().p("static {").ii(1).nl();
      while (remain>0) {
        String subClzName = className + "_" + its++;
        int len = Math.min(MAX_STRINGS_IN_CONST_POOL, remain);
        toClassWithArrayFill(sb4fillers, subClzName, values, s, len);
        sb.i().p(subClzName).p(".fill(VALUES);").nl();
        s += len;
        remain -= len;
      }
      sb.di(1).i().p("}").nl();
      sb.p(sb4fillers);
    }
    return sb.di(1).p("}").nl();
  }

  /** Maximum number of string generated per class (static initializer) */
  public static int MAX_STRINGS_IN_CONST_POOL = 3000;

  public static SB toClassWithArrayFill(SB sb, String clzName, String[] values, int start, int len) {
    sb.i().p("static final class ").p(clzName).p(" {").ii(1).nl();
    sb.i().p("static final void fill(String[] sa) {").ii(1).nl();
    for (int i=0; i<len; i++) {
      sb.i().p("sa[").p(start+i).p("] = ").ps(values[start+i]).p(";").nl();
    }
    sb.di(1).i().p("}").nl();
    sb.di(1).i().p("}").nl();
    return sb;
  }

  public static SB toClassWithArrayFill(SB sb, String clzName, float[] values, int start, int len) {
    sb.i().p("static final class ").p(clzName).p(" {").ii(1).nl();
    sb.i().p("static final void fill(float[] fa) {").ii(1).nl();
    for (int i=0; i<len; i++) {
      sb.i().p("fa[").p(start+i).p("] = ").pj(values[start + i]).p(";").nl();
    }
    sb.di(1).i().p("}").nl();
    sb.di(1).i().p("}").nl();
    return sb;
  }

  public static SB toField(SB sb, String modifiers, String type, String fname, String finit) {
    sb.i().p(modifiers).s().p(type).s().p(fname);
    if (finit!=null) sb.p(" = ").p(finit);
    sb.p(";").nl();
    return sb;
  }

  /**
   * Transform given string to legal java Identifier (see Java grammar http://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.8)
   *
   */
  public static String toJavaId(String s) {
    // Note that the leading 4 backslashes turn into 2 backslashes in the
    // string - which turn into a single backslash in the REGEXP.
    // "+-*/ !@#$%^&()={}[]|\\;:'\"<>,.?/"
    return s.replaceAll("\\+-\\* !@#\\$%\\^&\\()=\\{}\\[]\\|\\\\;:'\"<>,\\.\\?/",  "_");
  }
}
