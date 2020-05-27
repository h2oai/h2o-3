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
    final int[] comparedColumnsIndices = parseComparedColumnIndices(deduplicatedFrame, stk.track(asts[2].exec(env)));
    final String dropOrderString = asts[3].str();
    final KeepOrder keepOrder = EnumUtils.valueOfIgnoreCase(KeepOrder.class, dropOrderString)
            .orElseThrow(() -> new IllegalArgumentException(String.format("Unknown drop order: '%s'. Known types: %s",
                    dropOrderString, Arrays.toString(KeepOrder.values()))));

    final DropDuplicateRows dropDuplicateRows = new DropDuplicateRows(deduplicatedFrame, comparedColumnsIndices, keepOrder);
    final Frame outputFrame = dropDuplicateRows.dropDuplicates();
    return new ValFrame(outputFrame);
  }

  private int[] parseComparedColumnIndices(final Frame deduplicatedFrame, final Val comparedColumns) {
    final int[] columnRange;

    if (comparedColumns.isStr()) {
      final String columnName = comparedColumns.getStr();
      final int columnIndex = deduplicatedFrame.find(columnName);
      if (columnIndex == -1) {
        throw new IllegalArgumentException(String.format("Unknown column name: '%s'", columnName));
      }
      columnRange = new int[]{columnIndex};
    } else if (comparedColumns.isStrs()) {
      final String[] columnNames = comparedColumns.getStrs();
      columnRange = new int[columnNames.length];

      for (int i = 0; i < columnNames.length; i++) {
        final String columnName = columnNames[i];
        final int columnIndex = deduplicatedFrame.find(columnName);
        if (columnIndex == -1) {
          throw new IllegalArgumentException(String.format("Unknown column name: '%s'", columnName));
        }
        columnRange[i] = columnIndex;

      }

    } else if (comparedColumns.isNums()) {
      final double[] columnRangeDouble = comparedColumns.getNums();
      columnRange = new int[columnRangeDouble.length];
      for (int i = 0; i < columnRangeDouble.length; i++) {
        columnRange[i] = (int) columnRangeDouble[i];
        if (columnRange[i] < 0 || columnRange[i] > deduplicatedFrame.numCols() - 1) {
          throw new IllegalArgumentException(String.format("No such column index: '%d', frame has %d columns," +
                          "maximum index is %d.",
                  columnRange[i], deduplicatedFrame.numCols(), deduplicatedFrame.numCols() - 1));
        }
      }
    } else {
      throw new IllegalArgumentException(String.format("Column range for deduplication must either be a set of columns, or a " +
              "column range. Given type: %s", comparedColumns.type()));
    }

    return columnRange;
  }

  @Override
  public String str() {
    return "dropdup";
  }
}
