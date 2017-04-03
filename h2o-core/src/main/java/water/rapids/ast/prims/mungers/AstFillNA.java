package water.rapids.ast.prims.mungers;

import water.Freezable;
import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.*;
import water.rapids.ast.AstFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNum;
import water.rapids.ast.params.AstNumList;
import water.rapids.ast.params.AstStr;
import water.rapids.ast.params.AstStrList;
import water.rapids.ast.prims.mungers.AstGroup;
import water.rapids.ast.prims.reducers.AstMean;
import water.rapids.ast.prims.reducers.AstMedian;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValNums;
import water.util.ArrayUtils;
import water.util.IcedDouble;
import water.util.IcedHashMap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Fill NA's from previous or future values.
 * <p/> Different from impute in that a new Frame must be returned. It is
 * not inplace b/c a MRTask will create race conditions that will prevent correct results.
 * This function allows a limit of values to fill forward
 * <p/>
 */
public class AstFillNA extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "col", "method" };
  }

  @Override
  public String str() {
    return "h2o.fillna";
  }

  @Override
  public int nargs() {
    return 1 + 3;
  } // (h2o.impute data col method combine_method groupby groupByFrame values)

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    // Argument parsing and sanity checking
    // Whole frame being imputed
    Frame fr = stk.track(asts[1].exec(env)).getFrame();

    // Column within frame being imputed
    final int col = (int) asts[2].exec(env).getNum();
    if (col >= fr.numCols())
      throw new IllegalArgumentException("Column not -1 or in range 0 to " + fr.numCols());
    final boolean doAllVecs = col == -1;
    final Vec vec = doAllVecs ? null : fr.vec(col);
    Frame res = new FillDirectional("forward",10).doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame();
    return new ValFrame(res);
  }

  private static class FillDirectional extends MRTask<FillDirectional> {
    private final String _dir;
    private final int _maxLen;

    FillDirectional(String dir, int maxLen) {
      _dir = dir;
      _maxLen = maxLen;
    }

    @Override
    public void map(Chunk cs[], NewChunk nc[]) {
      for (int i = 0; i < cs.length; i++) {
        for (int j = 0; j < cs[i]._len; j++) {
          // current chunk element is NA
          if (cs[i].isNA(j)) {
            // search back to prev chunk if we are < maxLen away from j = 0
            if (j < _maxLen) {
              int searchCount = 0;
              Chunk searchChunk = cs[i];
              int searchStartIdx = j;
              int searchIdx = 0;
              while (searchChunk != null && searchCount < _maxLen && searchChunk.isNA(searchStartIdx - searchIdx)) {
                if (searchStartIdx - searchCount == 0) {
                  //reached the start of the chunk
                  searchChunk = searchChunk.vec().chunkForChunkIdx(searchChunk.cidx() - 1);
                  searchStartIdx = searchChunk.len() - 1;
                  searchIdx = 0;
                }
                searchCount++;
                searchIdx++;
              }
              // find the previous valid value up to maxLen distance
              // fill forward as much as you need and skip j forward by that amount

            } else {
              // otherwise keep moving forward
              nc[i].addNA();
              continue;
            }
          }
          // current chunk element not NA but next one is
          // fill as much as you have to
          // current chunk element not NA next one not NA
          // keep moving forward
        }
      }
    }

  }
}
