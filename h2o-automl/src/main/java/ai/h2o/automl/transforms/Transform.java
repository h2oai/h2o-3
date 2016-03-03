package ai.h2o.automl.transforms;

import org.reflections.Reflections;
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
 *
 *
 * Here are the features that are currently going to be generated:
 *      1. apply unary op to a single column
 *      2. multiply/add/subtract/divide two columns
 *      3. interact two columns (R style interaction)
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


  // for AutoCollect type scenario:
  //  1. decide if situation 1, 2, or 3
  //  2. "apply" the transformation (push the transformed vec onto the pile)

  // Since transformations will depend on
}
