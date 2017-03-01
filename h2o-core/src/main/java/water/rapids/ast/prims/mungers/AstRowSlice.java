package water.rapids.ast.prims.mungers;

import water.*;
import water.fvec.*;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstExec;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.params.AstId;
import water.rapids.ast.params.AstNum;
import water.rapids.ast.params.AstNumList;

import java.util.*;

/**
 * Row Slice
 */
public class AstRowSlice extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "rows"};
  }

  // (rows src [row_list])
  @Override
  public int nargs() {
    return 1 + 2;
  }

  @Override
  public String str() {
    return "rows";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Frame returningFrame;
    long nrows = fr.numRows();
    if (asts[2] instanceof AstNumList) {
      final AstNumList nums = (AstNumList) asts[2];

      if (!nums._isSort && !nums.isEmpty() && nums._bases[0] >= 0)
        throw new IllegalArgumentException("H2O does not currently reorder rows, please sort your row selection first");

      long[] rows = (nums._isList || nums.min() < 0) ? nums.expand8Sort() : null;
      if (rows != null) {
        if (rows.length == 0) {      // Empty inclusion list?
        } else if (rows[0] >= 0) { // Positive (inclusion) list
          if (rows[rows.length - 1] > nrows)
            throw new IllegalArgumentException("Row must be an integer from 0 to " + (nrows - 1));
        } else {                  // Negative (exclusion) list
          if (rows[rows.length - 1] >= 0)
            throw new IllegalArgumentException("Cannot mix negative and postive row selection");
          // Invert the list to make a positive list, ignoring out-of-bounds values
          BitSet bs = new BitSet((int) nrows);
          for (long row : rows) {
            int idx = (int) (-row - 1); // The positive index
            if (idx >= 0 && idx < nrows)
              bs.set(idx);        // Set column to EXCLUDE
          }
          rows = new long[(int) nrows - bs.cardinality()];
          for (int i = bs.nextClearBit(0), j = 0; i < nrows; i = bs.nextClearBit(i + 1))
            rows[j++] = i;
        }
      }
      final long[] ls = rows;

      returningFrame = new MRTask() {
        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
          if (nums.cnt() == 0) return;
          if (ls != null && ls.length == 0) return;
          long start = cs[0].start();
          long end = start + cs[0]._len;
          long min = ls == null ? (long) nums.min() : ls[0], max = ls == null ? (long) nums.max() - 1 : ls[ls.length - 1]; // exclusive max to inclusive max when stride == 1
          //     [ start, ...,  end ]     the chunk
          //1 []                          nums out left:  nums.max() < start
          //2                         []  nums out rite:  nums.min() > end
          //3 [ nums ]                    nums run left:  nums.min() < start && nums.max() <= end
          //4          [ nums ]           nums run in  :  start <= nums.min() && nums.max() <= end
          //5                   [ nums ]  nums run rite:  start <= nums.min() && end < nums.max()
          if (!(max < start || min > end)) {   // not situation 1 or 2 above
            long startOffset = (min > start ? min : start);  // situation 4 and 5 => min > start;
            for (int i = (int) (startOffset - start); i < cs[0]._len; ++i) {
              if ((ls == null && nums.has(start + i)) || (ls != null && Arrays.binarySearch(ls, start + i) >= 0)) {
                for (int c = 0; c < cs.length; ++c) {
                  if (cs[c] instanceof CStrChunk) ncs[c].addStr(cs[c], i);
                  else if (cs[c] instanceof C16Chunk) ncs[c].addUUID(cs[c], i);
                  else if (cs[c].isNA(i)) ncs[c].addNA();
                  else ncs[c].addNum(cs[c].atd(i));
                }
              }
            }
          }
        }
      }.doAll(fr.types(), fr).outputFrame(fr.names(), fr.domains());
    } else if ((asts[2] instanceof AstNum)) {
      long[] rows = new long[]{(long) (((AstNum) asts[2]).getNum())};
      returningFrame = fr.deepSlice(rows, null);
    } else if ((asts[2] instanceof AstExec) || (asts[2] instanceof AstId)) {
      Frame predVec = stk.track(asts[2].exec(env)).getFrame();
      if (predVec.numCols() != 1)
        throw new IllegalArgumentException("Conditional Row Slicing Expression evaluated to " + predVec.numCols() + " columns.  Must be a boolean Vec.");
      returningFrame = fr.deepSlice(predVec, null);
    } else
      throw new IllegalArgumentException("Row slicing requires a number-list as the last argument, but found a " + asts[2].getClass());
    return new ValFrame(returningFrame);
  }
}

