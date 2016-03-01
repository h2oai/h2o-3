package ai.h2o.automl.transforms;

import org.apache.commons.math3.util.FastMath;
import org.reflections.Reflections;
import water.H2O;
import water.Iced;
import water.rapids.ASTUniOp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Stupid class just to get the proverbial ball rolling.
 * Eventually, will want to javassist (i.e. fuse) transforms over multiple vecs
 * (rather than 1 at a time, which is the current limitation of this class).
 *
 * A pile of heuristics (rules) and models drive the choice of transforms by the column to
 * be transformed. The set of transforms (in the String[] ops) are the names of the AST
 * operations
 *
 *
 *
 * Some heuristics:
 *    Allowing users to do a log(), or log1p() quickly would be nice.
 *    Trying it behind their back might be useful. I've started to rattle off
 *        "if your numbers span more than an order of magnitude,x
 *         you might try a log transform"
 *    quite often. Reasonable enough heuristic. Heuristics are good for DSiaB.
 *
 *   Hard-coding a few common things to look for: "Year" is an integer, but log
 *   transforming it likely doesn't make as much sense; special transforms exist
 *   for that, so we might want to understand the difference and be willing to guess.
 */
public class Transform {
  public static final String[] uniOps;
  static {
    List<String> ops = new ArrayList<>();

    Reflections reflections = new Reflections("water.rapids");

    Set<Class<? extends ASTUniOp>> uniOpClasses =
            reflections.getSubTypesOf(water.rapids.ASTUniOp.class);

    for(Class c: uniOpClasses)
      ops.add(c.getSimpleName().substring("AST".length()));

   ops.add("Ignore"); ops.add("Impute"); ops.add("Cut");  // Ignore means do not include.. no transformation necessary!

    uniOps = ops.toArray(new String[ops.size()]);
  }
  public static void printOps() { for(String op: uniOps) System.out.println(op); }


  public abstract class Op extends Iced {
    double op(double a) { throw H2O.unimpl(); }
    double op(double a, double b) { throw H2O.unimpl(); }

    double doOp(double... dbls) {
      switch( dbls.length ) {
        case 1: return op(dbls[0]);
        case 2: return op(dbls[0],dbls[1]);
        default:
          throw new IllegalArgumentException("Expected 1 or 2 dbls. Got: " + dbls.length);
      }
    }
  }
  class Ceiling extends Op { double op(double d) { return Math.ceil (d); } }
  class Floor extends Op   { double op(double d) { return Math.floor(d); } }
  class Trunc extends Op   { double op(double d) { return d>=0?Math.floor(d):Math.ceil(d);}}
  class Cos  extends Op { double op(double d) { return Math.cos(d);}}
  class Sin  extends Op { double op(double d) { return Math.sin(d);}}
  class Tan  extends Op { double op(double d) { return Math.tan(d);}}
  class ACos extends Op {  double op(double d) { return Math.acos(d);}}
  class ASin extends Op {  double op(double d) { return Math.asin(d);}}
  class ATan extends Op { double op(double d) { return Math.atan(d);}}
  class Cosh extends Op { double op(double d) { return Math.cosh(d);}}
  class Sinh extends Op {  double op(double d) { return Math.sinh(d);}}
  class Tanh extends Op {  double op(double d) { return Math.tanh(d);}}
  class ACosh extends Op {  double op(double d) { return FastMath.acosh(d);}}
  class ASinh extends Op {  double op(double d) { return FastMath.asinh(d);}}
  class ATanh extends Op {  double op(double d) { return FastMath.atanh(d);}}
  class CosPi extends Op {  double op(double d) { return Math.cos(Math.PI*d);}}
  class SinPi extends Op {  double op(double d) { return Math.sin(Math.PI*d);}}
  class TanPi extends Op {  double op(double d) { return Math.tan(Math.PI*d);}}
  class Abs  extends Op {  double op(double d) { return Math.abs(d);}}
  class Sgn  extends Op {  double op(double d) { return Math.signum(d);}}
  class Sqrt extends Op {  double op(double d) { return Math.sqrt(d);}}
  class Log  extends Op {  double op(double d) { return Math.log(d);}}
  class Log10  extends Op {  double op(double d) { return Math.log10(d);}}
  class Log2  extends Op {  double op(double d) { return Math.log(d)/Math.log(2);}}
  class Log1p  extends Op {  double op(double d) { return Math.log1p(d);}}
  class Exp  extends Op {  double op(double d) { return Math.exp(d);}}
  class Expm1  extends Op {  double op(double d) { return Math.expm1(d);}}
  class Gamma  extends Op {  double op(double d) {  return org.apache.commons.math3.special.Gamma.gamma(d);}}
  class LGamma extends Op {  double op(double d) { return org.apache.commons.math3.special.Gamma.logGamma(d);}}
  class DiGamma  extends Op { double op(double d) {  return Double.isNaN(d)?Double.NaN:org.apache.commons.math3.special.Gamma.digamma(d);}}
  class TriGamma  extends Op {double op(double d) {  return Double.isNaN(d)?Double.NaN:org.apache.commons.math3.special.Gamma.trigamma(d);}}
}
