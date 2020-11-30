package water.rapids.ast.prims.filters.dropduplicates;

import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.util.EnumUtils;

import java.util.Arrays;

/**
 * Removes duplicated rows, leaving only the first or last observed duplicate in place.
 */
public class AstDropDuplicates extends AstPrimitive<AstDropDuplicates> {
  @Override
  public int nargs() {
    return 1 + 3;
  }

  @Override
  public String[] args() {
    return new String[]{"ary", "frame", "cols", "droporder"};
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
    final Frame deduplicatedFrame = stk.track(asts[1].exec(env)).getFrame();
    final int[] comparedColumnsIndices = ColumnIndicesParser.parseAndCheckComparedColumnIndices(deduplicatedFrame,
            stk.track(asts[2].exec(env)));
    final String dropOrderString = asts[3].str();
    final KeepOrder keepOrder = EnumUtils.valueOfIgnoreCase(KeepOrder.class, dropOrderString)
            .orElseThrow(() -> new IllegalArgumentException(String.format("Unknown drop order: '%s'. Known types: %s",
                    dropOrderString, Arrays.toString(KeepOrder.values()))));

    final DropDuplicateRows dropDuplicateRows = new DropDuplicateRows(deduplicatedFrame, comparedColumnsIndices, keepOrder);
    final Frame outputFrame = dropDuplicateRows.dropDuplicates();
    return new ValFrame(outputFrame);
  }


  @Override
  public String str() {
    return "dropdup";
  }
}
