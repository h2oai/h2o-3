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
