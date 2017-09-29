package water.rapids.ast.prims.mungers;

import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.Merge;
import water.rapids.ast.AstParameter;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstStrList;
import water.rapids.vals.ValFrame;

import java.util.Arrays;

import static water.util.ArrayUtils.initBooleanArrays;


/** Sort the whole frame by the given columns.  However, an error will be thrown if there are
 * String columns within the frame.
 */
public class AstSort extends AstPrimitive {
  @Override public String[] args() { return new String[]{"ary","cols"}; }
  @Override public String str(){ return "sort";}
  @Override public int nargs() { return 1+2+1; } // (sort ary [cols] [boolean])

  @Override public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    int[] cols = ((AstParameter)asts[2]).columns(fr.names());
    int numCols = cols.length;
    boolean[] sortAsc;
    if (asts.length == 4) {
      if (asts[3] instanceof AstStrList) { // R client send a string list, convert to boolean[]
        sortAsc = convertStrA2BoolA(((AstStrList) asts[3])._strs);
      } else {  // R can send a string all by itself
        if (asts[3].str().toLowerCase().equals("true"))
          sortAsc = initBooleanArrays(numCols, true);
        else
          sortAsc = initBooleanArrays(numCols, false);
      }
    } else   // may have been called without enough arguments.  Sorting will be ascending by default
      sortAsc = initBooleanArrays(numCols, true);

    String[] colTypes = fr.typesStr();
    assert sortAsc.length==cols.length;
    if (Arrays.asList(colTypes).contains("String"))
      throw new IllegalArgumentException("Input frame contains String columns.  Remove String columns before " +
              "calling sort again.");
    return new ValFrame(Merge.sort(fr,cols, sortAsc));
  }

  public static boolean[] convertStrA2BoolA(String[] ascStr) {
    int numCols = ascStr.length;
    boolean[] boolA = new boolean[numCols];
    for (int index = 0; index < numCols; index++) {
      if (ascStr[index].toLowerCase().equals("false"))
        boolA[index]=false;
      else
        boolA[index]=true;
    }
    return boolA;
  }
}
