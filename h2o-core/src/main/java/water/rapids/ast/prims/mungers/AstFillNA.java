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

  private static final String METHOD_BACKWARD = "backward";

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

    final int axis = (int) asts[3].exec(env).getNum();
    if (!(Arrays.asList(0,1)).contains(axis))
      throw new IllegalArgumentException("Axis must be 0 for columnar 1 for row");
    final int limit = (int) asts[4].exec(env).getNum();

    assert limit >= 0:"The maxlen/limit parameter should be >= 0.";
    if (limit == 0) // fast short cut to do nothing if user set zero limit
      return new ValFrame(fr.deepCopy(Key.make().toString()));

    Frame res;

    if (axis == 0) {
      res = (METHOD_BACKWARD.equalsIgnoreCase(method.trim()))?
              new FillBackwardTaskCol(limit, fr.anyVec().nChunks()).doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame():
              new FillForwardTaskCol(limit).doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame();
    } else {
      res = (METHOD_BACKWARD.equalsIgnoreCase(method.trim()))?
              new FillBackwardTaskRow(limit).doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame():
              new FillForwardTaskRow(limit).doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame();
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
  
  private static class FillBackwardTaskRow extends MRTask<FillBackwardTaskRow> {
    private final int _maxLen;

    FillBackwardTaskRow(int maxLen) { _maxLen = maxLen;}

    @Override
    public void map(Chunk cs[], NewChunk nc[]) {
      int lastCol = cs.length-1;  // index of last column in the chunk
      for (int i = 0; i < cs[0]._len; i++) {  // go through each row
        int fillCount = 0;
        nc[lastCol].addNum(cs[lastCol].atd(i)); // copy over last row element regardless
        for (int j = lastCol-1; j >= 0; j--) {  // going backwards
          if (cs[j].isNA(i)) {
            int lastNonNACol = j+1;
            if (!nc[lastNonNACol].isNA(i) && fillCount < _maxLen) {
              nc[j].addNum(nc[lastNonNACol].atd(i));
              fillCount++;
            } else {
              nc[j].addNA(); // keep the NaNs, run out ot maxLen
            }
          } else {
            if (fillCount > 0) fillCount = 0; // reset fillCount after encountering a non NaN.
            nc[j].addNum(cs[j].atd(i)); // no NA filling needed, element is not NaN
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

  private static class FillBackwardTaskCol extends MRTask<FillBackwardTaskCol> {
    private final int _maxLen;
    private final int _lastChunkIndex;

    FillBackwardTaskCol(int maxLen, int chunksNum) {
      _maxLen = maxLen;
      _lastChunkIndex= chunksNum-1;
    }

    @Override
    public void map(Chunk cs[], NewChunk nc[]) {
      int lastRowIndex = cs[0]._len-1;
      int currentCidx = cs[0].cidx();
      double[] newChunkInfo = new double[cs[0].len()];  // allocate once per column chunk
      int chkLen = cs[0].len();

      for (int i = 0; i < cs.length; i++) {
        int naBlockRowStart = -1; // row where we see our first NA
        int lastNonNaNRow = -1; // indicate row where the element is not NaN
        int rowIndex = lastRowIndex;
        int naBlockLength = 0;
        double fillVal=Double.NaN;
        int fillLen = 0;  // number of values to be filled for NAs

        while (rowIndex > -1) { // search backwards from end of chunk
          if (cs[i].isNA(rowIndex)) { // found an NA in a row
            naBlockRowStart= rowIndex;
            rowIndex--; // drop the row index
            naBlockLength++;
            while ((rowIndex > -1) && cs[i].isNA(rowIndex)) {  // want to find all NA blocks in this chunk
              naBlockLength++;
              rowIndex--;
            }

            // done finding a NA block in the chunk column
            if (lastNonNaNRow < 0) {  // has not found an non NaN element in this chunk, from next chunk then
              if (currentCidx == _lastChunkIndex) { // this is the last chunk, nothing to look back to
                fillLen = 0;
              } else {
                fillLen = _maxLen;
                boolean foundFillVal = false;
                for (int cIndex = currentCidx+1; cIndex <= _lastChunkIndex; cIndex++) {
                  if (foundFillVal) // found fill value in next chunk
                    break;
                  // search the next chunk for nonNAs
                  Chunk nextChunk = cs[i].vec().chunkForChunkIdx(cIndex); // grab the next chunk
                  int nChunkLen = nextChunk.len();
                  for (int rIndex=0; rIndex < nChunkLen; rIndex++) {
                    if (nextChunk.isNA(rIndex)) {
                      fillLen--;  // reduce fillLen here
                    } else {  // found a No NA row
                      fillVal = nextChunk.atd(rIndex);
                      foundFillVal = true;
                      break;
                    }
                    if (fillLen < 1) {  // no fill values is found in this chunk
                      break;
                    }
                  }
                }
              }
            } else {  // found non NaN element in this chunk, can copy over values if valid
              fillVal = cs[i].atd(lastNonNaNRow);
              fillLen = _maxLen;  // can fill as many as the maxLen here
            }

            // fill the chunk then with fillVal is fillLen > 0, otherwise, fill it with NaNs
            int naRowEnd = naBlockRowStart-naBlockLength;
            for (int naRow = naBlockRowStart; naRow > naRowEnd; naRow--) {
              if (fillLen > 0) {
                newChunkInfo[naRow] = fillVal; // nc[i].addNum(fillVal);
                fillLen--;
              } else {
                newChunkInfo[naRow] = Double.NaN; // nc[i].addNA();
              }
            }
            // finished filling in the NAs, need to reset counts
            naBlockLength=0;
            lastNonNaNRow = -1;
          } else {
            newChunkInfo[rowIndex] =  cs[i].atd(rowIndex); // nc[i].addNum(cs[i].atd(rowIndex));
            lastNonNaNRow = rowIndex;
            rowIndex--;
            naBlockLength=0;
          }
        }
        // copy info from newChunkInfo to NewChunk
        for (int rindex=0; rindex < chkLen; rindex++) {
          nc[i].addNum(newChunkInfo[rindex]);
        }
      }
    }
  }
}
