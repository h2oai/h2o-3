package water.rapids.ast.prims.mungers;

import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.*;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

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
    return new String[]{"ary", "method", "axis", "limit" };
  }

  @Override
  public String str() {
    return "h2o.fillna";
  }

  @Override
  public int nargs() {
    return 1 + 4;
  } // (h2o.impute data col method combine_method groupby groupByFrame values)

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    // Argument parsing and sanity checking
    // Whole frame being imputed
    Frame fr = stk.track(asts[1].exec(env)).getFrame();

    // Column within frame being imputed
    final String method = asts[2].exec(env).getStr();
    final int axis = (int) asts[3].exec(env).getNum();
    final int limit = (int) asts[4].exec(env).getNum();
    Frame res = new FillDirectional("forward",limit).doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame();
    res._key = Key.<Frame>make();
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
              // find the previous valid value up to maxLen distance
              while (searchChunk != null && searchCount < _maxLen && searchChunk.isNA(searchStartIdx - searchIdx)) {
                if (searchStartIdx - searchCount == 0) {
                  //reached the start of the chunk
                  if (searchChunk.cidx() > 0) {
                    searchChunk = searchChunk.vec().chunkForChunkIdx(searchChunk.cidx() - 1);
                    searchStartIdx = searchChunk.len() - 1;
                    searchIdx = 0;
                    searchCount++;
                    continue;
                  } else {
                    searchChunk = null;
                  }
                }
                searchIdx++;
                searchCount++;
              }
              if (searchChunk == null) {
                nc[i].addNA();
              } else {
                // fill forward as much as you need and skip j forward by that amount
                double fillVal = searchChunk.atd(searchStartIdx - searchIdx);
                int fillCount = _maxLen - searchCount;
                fillCount = Math.min(fillCount,cs[i]._len);
                for (int f = 0; f<fillCount; f++) {
                  nc[i].addNum(fillVal);
                  fillCount++;
                }
                j += (fillCount - 1);
              }

            } else {
              // otherwise keep moving forward
              nc[i].addNA();
            }
          } else if (j < cs[i]._len -1 && !cs[i].isNA(j) && cs[i].isNA(j+1)) {
            // current chunk element not NA but next one is
            // fill as much as you have to
            double fillVal = cs[i].atd(j);
            nc[i].addNum(fillVal);
            int fillCount = 0; j++;
            while (j+fillCount < cs[i]._len && fillCount < _maxLen && cs[i].isNA(j+fillCount)) {
               nc[i].addNum(fillVal);
               fillCount++;
            }
            j += (fillCount - 1);

          } else {
            // current chunk element not NA next one not NA
            // keep moving forward
            nc[i].addNum(cs[i].atd(j));
          }
        }
      }
    }

  }
}
