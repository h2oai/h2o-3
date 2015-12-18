package water.codegen;

import water.util.IcedBitSet;

/**
 * Simple interface to share definition of SB and SBPrintStream.
 *
 * Designed for Java code generation.
 */
public interface JCodeSB<T extends JCodeSB> {

  T p(String s);

  T p(float s);

  T p(double s);

  T p(char s);

  T p(int s);

  T p(long s);

  T p(boolean s);

  T p(JCodeSB s);

  T pobj(Object s);

  T p(Enum e);

  /** Increase indentation counter */
  T i(int d);

  /** Increase indentation counter */
  T i();

  /** Indent and append. */
  T ip(String s);

  /** Append empty string. */
  T s();

  // Java specific append of double
  T pj(double s);

  // Java specific append of float
  T pj(float s);

  /* Append Java string - escape all " and \ and encapsulate it into "..." */
  T pj(String s);

  /** Append full name of class. */
  T pj(Class c);

  /** Append Java Long */
  T pj(long l);

  /** Append Java array as new double[] { val1, val2, ...} */
  T pj(double[] ary);

  /** Print number of [] based on passed dimension */
  T pbraces(int dim);

  /* Append line comment */
  T lineComment(String s);

  T blockComment(String s);

  /** Append reference to object's field
   *
   * @param objectName  name of object
   * @param fieldName  field name to reference
   * @return
   */
  T pj(String objectName, String fieldName);

  T p(IcedBitSet ibs);

  /** Increase indentation counter */
  T ii(int i);

  /** Decrease indentation counter */
  T di(int i);

  // Copy indent from given string buffer
  T ci(JCodeSB sb);

  /** Output new line */
  T nl();

  /** Output number of new lines. */
  T nl(int n);

  // Convert a String[] into a valid Java String initializer
  T toJavaStringInit(String[] ss);

  T toJavaStringInit(float[] ss);

  T toJavaStringInit(double[] ss);

  T toJavaStringInit(double[][] ss);

  T toJavaStringInit(double[][][] ss);

  T toJSArray(float[] nums);

  T toJSArray(String[] ss);

  int getIndent();

  String getContent();
}
