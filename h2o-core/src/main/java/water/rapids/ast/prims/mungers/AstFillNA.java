package water.rapids.ast.prims.mungers;

import water.H2O;
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

import java.util.Arrays;

/**
 * Fill NA's from previous or future values.
 * <p/> Different from impute in that a new Frame must be returned. It is
 * not inplace b/c a MRTask will create race conditions that will prevent correct results.
 * This function allows a limit of values to fill forward
 * <p/>
 * @param method Direction to fill either: forward or backward
 * @param axis Along which axis to fill, 0 for columnar, 1 for row
 * @param limit Max number of consecutive NA's to fill
 * @return New Frame with filled values
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
    if (!(Arrays.asList("forward","backward")).contains(method.toLowerCase()))
      throw new IllegalArgumentException("Method must be forward or backward");
    // Not impl yet
    if (method.toLowerCase() == "backward")
      throw H2O.unimpl("Backward fillna not implemented yet");

    final int axis = (int) asts[3].exec(env).getNum();
    if (!(Arrays.asList(0,1)).contains(axis))
      throw new IllegalArgumentException("Axis must be 0 for columnar 1 for row");
    final int limit = (int) asts[4].exec(env).getNum();
    Frame res;
    if (axis == 0) {
      res = new FillForwardTaskCol(limit).doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame();
    } else {
      res = new FillForwardTaskRow(limit).doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame();
    }
    res._key = Key.<Frame>make();
    return new ValFrame(res);
  }

  private static class FillForwardTaskRow extends MRTask<FillForwardTaskRow> {
    private final int _maxLen;

    FillForwardTaskRow(int maxLen) { _maxLen = maxLen; }

    @Override
    public void map(Chunk cs[], NewChunk nc[]) {
      for (int i = 0; i < cs[0]._len; i++) {
        int fillCount = 0;
        nc[0].addNum(cs[0].atd(i));
        for (int j = 1; j < cs.length; j++) {
          if (cs[j].isNA(i)) {
            if (!nc[j-1].isNA(i) && fillCount < _maxLen) {
              nc[j].addNum(nc[j-1].atd(i));
              fillCount++;
            } else {
              nc[j].addNA();
            }
          } else {
            if (fillCount > 0) fillCount = 0;
            nc[j].addNum(cs[j].atd(i));
          }
        }
      }
    }
  }
  private static class FillForwardTaskCol extends MRTask<FillForwardTaskCol> {
    private final int _maxLen;

    FillForwardTaskCol(int maxLen) {
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
                // How many fills forward from this row is ok?
                int maxFill = 1;
                int k = 1;
                while(cs[i].isNA(j+k)) {
                  k++;
                  maxFill++;
                }
                fillCount = Math.min(maxFill, fillCount);
                // We've searched back but maxlen isnt big enough to propagate here.
                if (fillCount < 0)
                  nc[i].addNA();
                else if (fillCount == 0)
                  nc[i].addNum(fillVal);
                else
                  for (int f = 0; f<fillCount; f++) { nc[i].addNum(fillVal); }

                fillCount = Math.max(1,fillCount);
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
