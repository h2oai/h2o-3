package water.exceptions;

import water.util.IcedBitSet;

/**
 * Simple interface to share definition of SB and SBPrintStream.
 *
 * Designed for Java code generation.
 */
public interface JCodeSB<T extends JCodeSB> {

  // Append primitves
  T ps(String s);

  T p(String s);

  T p(float s);

  T p(double s);

  T p(char s);

  T p(int s);

  T p(long s);

  T p(boolean s);

  T p(JCodeSB s);

  T pobj(Object s);

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

  /* Append Java string - escape all " and \ */
  T pj(String s);

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

  T nl();

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
