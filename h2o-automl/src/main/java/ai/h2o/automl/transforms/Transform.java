package ai.h2o.automl.transforms;

import java.util.ArrayList;
import java.util.List;

/**
 * Stupid class just to get the proverbial ball rolling.
 * Eventually, will want to javassist (i.e. fuse) transforms over multiple vecs
 * (rather than 1 at a time, which is the current limitation of this class).
 *
 * A pile of heuristics (rules) and models drive the choice of transforms by the column to
 * be transformed. The set of transforms (in the String[] ops) are the names of the AST
 * operations
 *
 * Here are the features that are currently going to be generated:
 *      1. apply unary op to a single column
 *      2. multiply/add/subtract/divide two columns
 *      3. interact two columns (R style interaction)
 */
public class Transform {
  public static final String[] ops;
  public static final String[] basicOps;
  public static final String[] binaryOps;
  public static final String[] timeOps;
  public static final String[] advancedOps;

  static {
    List<String> basicOpsList    = new ArrayList<>();
    List<String> binaryOpsList   = new ArrayList<>();
    List<String> timeOpsList     = new ArrayList<>();
    List<String> advancedOpsList = new ArrayList<>();
    List<String> opsList = new ArrayList<>();


    basicOpsList.add("ignore");  // Ignore means do not include.. no transformation necessary!
    basicOpsList.add("log2"); basicOpsList.add("log10"); basicOpsList.add("log");
    binaryOpsList.add("+"); binaryOpsList.add("-"); binaryOpsList.add("*"); binaryOpsList.add("/");
    timeOpsList.add("year"); timeOpsList.add("day"); timeOpsList.add("hour"); timeOpsList.add("minute");
    timeOpsList.add("second"); timeOpsList.add("millis"); timeOpsList.add("month"); timeOpsList.add("month");
    timeOpsList.add("week"); timeOpsList.add("dayOfWeek");
    advancedOpsList.add("Impute"); advancedOpsList.add("Cut");

    opsList.addAll(basicOpsList);
    opsList.addAll(binaryOpsList);
    opsList.addAll(timeOpsList);
    opsList.addAll(advancedOpsList);
    ops = opsList.toArray(new String[opsList.size()]);
    basicOps = basicOpsList.toArray(new String[basicOpsList.size()]);
    binaryOps = binaryOpsList.toArray(new String[binaryOpsList.size()]);
    timeOps = timeOpsList.toArray(new String[timeOpsList.size()]);
    advancedOps = advancedOpsList.toArray(new String[advancedOpsList.size()]);
  }
  public static void printOps() { for(String op: ops) System.out.println(op); }
}



//    Reflections reflections = new Reflections("water.rapids");
//
//    Set<Class<? extends ASTUniOp>> uniOpClasses = reflections.getSubTypesOf(water.rapids.ASTUniOp.class);
//
//    for(Class c: uniOpClasses)
//      ops.add(c.getSimpleName().substring("AST".length()));