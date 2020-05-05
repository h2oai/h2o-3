package water.rapids;

import water.*;
import water.fvec.*;
import water.util.Log;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static java.math.BigInteger.ZERO;
import static java.math.BigInteger.ONE;
import static water.rapids.SingleThreadRadixOrder.getSortedOXHeaderKey;

public class Merge {
  
  public static int ASCENDING = 1;
  public static int DESCENDING = -1; 

  public static Frame sort(final Frame fr, int col) {
    return sort(fr, new int[]{col});
  }
  
  public static Frame sort(final Frame fr, int[] cols) {
    int numCol = cols.length;
    int[] ascending = new int[numCol];
    Arrays.fill(ascending,1);

    return sort(fr, cols, ascending); // default is to sort in ascending order
  }
  
  // Radix-sort a Frame using the given columns as keys.
  // This is a fully distributed and parallel sort.
  // It is not currently an in-place sort, so the data is doubled and a sorted copy is returned.
  public static Frame sort(final Frame fr, int[] cols, int[] ascending) {
    if( cols.length==0 )        // Empty key list
      return fr;                // Return original frame
    for( int col : cols )
      if( col < 0 || col >= fr.numCols() )
        throw new IllegalArgumentException("Column "+col+" is out of range of "+fr.numCols());
    // All identity ID maps
    int id_maps[][] = new int[cols.length][];
    for( int i=0; i<cols.length; i++ ) {
      Vec vec = fr.vec(cols[i]);
      if( vec.isCategorical() ) {
        String[] domain = vec.domain();
        id_maps[i] = new int[domain.length];
        for( int j=0; j<domain.length; j++ ) id_maps[i][j] = j;
      }
    }

    return Merge.merge(fr, new Frame(new Vec[0]), cols, new int[0], true/*allLeft*/, id_maps, ascending, new int[0]);
  }



  public static Frame merge(final Frame leftFrame, final Frame riteFrame, final int leftCols[], final int riteCols[],
                            boolean allLeft, int[][] id_maps) {
    int[] ascendingL, ascendingR;
    if (leftCols != null && leftCols.length>0) {
      ascendingL = new int[leftCols.length];
      Arrays.fill(ascendingL, 1);
    } else
      ascendingL = new int[0];

    if (riteCols != null && riteCols.length > 0) {
      ascendingR = new int[riteCols.length];
      Arrays.fill(ascendingR, 1);
    } else
      ascendingR = new int[0];


    return merge(leftFrame, riteFrame, leftCols, riteCols, allLeft, id_maps, ascendingL, ascendingR);
  }
  // single-threaded driver logic.  Merge left and right frames based on common columns.
  public static Frame merge(final Frame leftFrame, final Frame riteFrame, final int leftCols[], final int riteCols[],
                            boolean allLeft, int[][] id_maps, int[] ascendingL, int[] ascendingR) {
    if (allLeft && (riteFrame.numRows()==0))
      return sortOnly(leftFrame,  leftCols, id_maps, ascendingL);
    final boolean hasRite = riteCols.length > 0;

    // if there are NaN or null values in the rite frames in the merge columns, it is decided by Matt Dowle to not
    // include those rows in the final merged frame.  Hence, I am going to first remove the na rows in the
    // mergedCols.
    boolean naPresent = false;  // true if there are nas in merge columns
    if (riteFrame != null) {
      for (int colidx : riteCols)
        if (riteFrame.vec(colidx).naCnt() > 0) {
          naPresent = true;
          break;
        }
    }

    Frame rightFrame = naPresent ? new RemoveNAsTask(riteCols)
            .doAll(riteFrame.types(), riteFrame).outputFrame(riteFrame.names(), riteFrame.domains())
            : riteFrame;


    // map missing levels to -1 (rather than increasing slots after the end)
    // for now to save a deep branch later
    for (int i=0; i<id_maps.length; i++) {
      if (id_maps[i] == null) continue;
      assert id_maps[i].length >= leftFrame.vec(leftCols[i]).max()+1
              :"Left frame cardinality is higher than right frame!  Switch frames and change merge directions to get " +
              "around this restriction.";
      if( !hasRite ) continue;
      int right_max = (int)rightFrame.vec(riteCols[i]).max();
      for (int j=0; j<id_maps[i].length; j++) {
        assert id_maps[i][j] >= 0;
        if (id_maps[i][j] > right_max) id_maps[i][j] = -1;
      }
    }

    // Running 3 consecutive times on an idle cluster showed that running left
    // and right in parallel was a little slower (97s) than one by one (89s).
    // empty frame will come back with base = Long.MIN_VALUE (-9223372036854775808).  
    // TODO: retest in future
    RadixOrder leftIndex = createIndex(true ,leftFrame,leftCols,id_maps, ascendingL);
    RadixOrder riteIndex = createIndex(false,rightFrame,riteCols,id_maps, ascendingR);

    // TODO: start merging before all indexes had been created. Use callback?
    boolean leftFrameEmpty = (leftFrame.numRows()==0);
    boolean riteFrameEmpty = (riteFrame.numRows()==0);
    Log.info("Making BinaryMerge RPC calls ... ");
    long t0 = System.nanoTime();
    ArrayList<BinaryMerge> bmList = new ArrayList<>();
    Futures fs = new Futures();
    final int leftShift = leftFrameEmpty?-1:leftIndex._shift[0];
    final BigInteger leftBase = leftFrameEmpty?ZERO:leftIndex._base[0];
    final int riteShift = riteFrameEmpty?-1:riteIndex._shift[0];
    final BigInteger riteBase = riteFrameEmpty?ZERO : riteIndex._base [0];

    // initialize for double columns, may not be used....
    long leftMSBfrom = riteBase.subtract(leftBase).shiftRight(leftShift).longValue();  // value when all frames nonempty
    boolean riteBaseExceedsleftBase=riteFrameEmpty?false:riteBase.compareTo(leftBase)>0;
    // deal with the left range below the right minimum, if any
    if (riteBaseExceedsleftBase) {  // right branch has higher minimum column value
      // deal with the range of the left below the start of the right, if any
      assert leftMSBfrom >= 0;
      if (leftMSBfrom>255) {
        // The left range ends before the right range starts.  So every left row is a no-match to the right
        leftMSBfrom = 256;  // so that the loop below runs for all MSBs (0-255) to fetch the left rows only
      }
      // run the merge for the whole lefts that end before the first right.
      // The overlapping one with the right base is dealt with inside
      // BinaryMerge (if _allLeft)
      if (allLeft) for (int leftMSB=0; leftMSB<leftMSBfrom; leftMSB++) {
        BinaryMerge bm = new BinaryMerge(new BinaryMerge.FFSB(leftFrame, leftMSB, leftShift,
                leftIndex._bytesUsed, leftIndex._base), new BinaryMerge.FFSB(rightFrame,/*rightMSB*/-1, riteShift,
                riteIndex._bytesUsed, riteIndex._base),
                true);
          bmList.add(bm);
          fs.add(new RPC<>(SplitByMSBLocal.ownerOfMSB(leftMSB), bm).call());
        }
    } else {
      // completely ignore right MSBs below the left base
      assert leftMSBfrom <= 0;
      leftMSBfrom = 0;
    }

    BigInteger rightS = BigInteger.valueOf(256L<<riteShift);
    long leftMSBto = leftFrameEmpty?0:riteBase.add(rightS).subtract(ONE).subtract(leftBase).shiftRight(leftShift).longValue();
    // deal with the left range above the right maximum, if any.  For doubles, -1 from shift to avoid negative outcome
    boolean leftRangeAboveRightMax = leftIndex._isCategorical[0]?
            leftBase.add(BigInteger.valueOf(256L<<leftShift)).compareTo(riteBase.add(rightS)) > 0:
            leftBase.add(BigInteger.valueOf(256L<<leftShift)).compareTo(riteBase.add(rightS)) >= 0;

    if (leftRangeAboveRightMax) { //
      assert leftMSBto <= 255;
      if (leftMSBto<0) {
        // The left range starts after the right range ends.  So every left row
        // is a no-match to the right
        leftMSBto = -1;  // all MSBs (0-255) need to fetch the left rows only
      }
      // run the merge for the whole lefts that start after the last right
      if (allLeft) for (int leftMSB=(int)leftMSBto+1; leftMSB<=255; leftMSB++) {
        BinaryMerge bm = new BinaryMerge(new BinaryMerge.FFSB(leftFrame,   leftMSB    ,leftShift,
                leftIndex._bytesUsed,leftIndex._base),
                new BinaryMerge.FFSB(rightFrame,/*rightMSB*/-1,riteShift,
                        riteIndex._bytesUsed,riteIndex._base),
                true);
          bmList.add(bm);
          fs.add(new RPC<>(SplitByMSBLocal.ownerOfMSB(leftMSB), bm).call());
      }
    } else if (!leftFrameEmpty){
      // completely ignore right MSBs after the right peak
        assert leftMSBto >= 255;
        leftMSBto = 255;
    }

    // the overlapped region; i.e. between [ max(leftMin,rightMin), min(leftMax, rightMax) ]
    // when right frame is empty, the leftMSBto will be 9223372036854775808 and hence the code will
    // stall here.  I have changed the way leftMSBto in order to avoid this problem.
    assert leftMSBfrom >= 0;
    assert leftMSBto <= 255;
    for (int leftMSB = (int) leftMSBfrom; leftMSB <= leftMSBto; leftMSB++) {
      // calculate the key values at the bin extents:  [leftFrom,leftTo] in terms of keys
      long leftFrom = leftFrameEmpty ? 0 : ((((long) leftMSB) << leftShift) - 1 + leftBase.longValue());  // -1 for leading NA spot
      long leftTo = leftFrameEmpty ? 0 : (((((long) leftMSB + 1) << leftShift) - 1 + leftBase.longValue()) - 1);  // -1 for leading NA spot and another -1 to get last of previous bin

      // which right bins do these left extents occur in (could span multiple, and fall in the middle)
      int rightMSBfrom = (int) ((leftFrom - (riteFrameEmpty ? 0 : riteBase.longValue()) + 1) >> riteShift);   // +1 again for the leading NA spot
      int rightMSBto = (int) ((leftTo - (riteFrameEmpty ? 0 : riteBase.longValue()) + 1) >> riteShift);

      // the non-matching part of this region will have been dealt with above when allLeft==true
      if (rightMSBfrom < 0) rightMSBfrom = 0;
      assert rightMSBfrom <= 255;
      if (rightMSBto > 255) rightMSBto = 255;
      assert rightMSBto >= rightMSBfrom;

      for (int rightMSB = rightMSBfrom; rightMSB <= rightMSBto; rightMSB++) {
        BinaryMerge bm = new BinaryMerge(new BinaryMerge.FFSB(leftFrame, leftMSB, leftShift, leftIndex._bytesUsed, leftIndex._base),
                new BinaryMerge.FFSB(rightFrame, rightMSB, riteShift, riteIndex._bytesUsed, riteIndex._base),
                allLeft);
        bmList.add(bm);
        // TODO: choose the bigger side to execute on (where that side of index
        // already is) to minimize transfer.  within BinaryMerge it will
        // recalculate the extents in terms of keys and bsearch for them within
        // the (then local) both sides
        H2ONode node = SplitByMSBLocal.ownerOfMSB(rightMSB);
        fs.add(new RPC<>(node, bm).call());
      }
    }
    Log.debug("took: " + String.format("%.3f", (System.nanoTime() - t0) / 1e9) +" seconds.");

    t0 = System.nanoTime();
    Log.info("Sending BinaryMerge async RPC calls in a queue ... ");
    fs.blockForPending();
    
    Log.debug("took: " + (System.nanoTime() - t0) / 1e9+" seconds.");
    
    Log.debug("Removing DKV keys of left and right index.  ... ");
    // TODO: In future we won't delete but rather persist them as index on the table
    // Explicitly deleting here (rather than Arno's cleanUp) to reveal if we're not removing keys early enough elsewhere
    t0 = System.nanoTime();
    for (int msb=0; msb<256; msb++) {
      for (int isLeft=0; isLeft<2; isLeft++) {
        Key k = getSortedOXHeaderKey(isLeft!=0, msb);
        SingleThreadRadixOrder.OXHeader oxheader = DKV.getGet(k);
        DKV.remove(k);
        if (oxheader != null) {
          for (int b=0; b<oxheader._nBatch; ++b) {
            k = SplitByMSBLocal.getSortedOXbatchKey(isLeft!=0, msb, b);
            DKV.remove(k);
          }
        }
      }
    }
    Log.debug("took: " + (System.nanoTime() - t0)/1e9+" seconds.");

    Log.info("Allocating and populating chunk info (e.g. size and batch number) ...");
    t0 = System.nanoTime();
    long ansN = 0;
    int numChunks = 0;
    for( BinaryMerge thisbm : bmList )
      if( thisbm._numRowsInResult > 0 ) {
        numChunks += thisbm._chunkSizes.length;
        ansN += thisbm._numRowsInResult;
      }
    long chunkSizes[] = new long[numChunks];
    int chunkLeftMSB[] = new int[numChunks];  // using too much space repeating the same value here, but, limited
    int chunkRightMSB[] = new int[numChunks];
    int chunkBatch[] = new int[numChunks];
    int k = 0;
    for( BinaryMerge thisbm : bmList ) {
      if (thisbm._numRowsInResult == 0) continue;
      int thisChunkSizes[] = thisbm._chunkSizes;
      for (int j=0; j<thisChunkSizes.length; j++) {
        chunkSizes[k] = thisChunkSizes[j];
        chunkLeftMSB [k] = thisbm._leftSB._msb;
        chunkRightMSB[k] = thisbm._riteSB._msb;
        chunkBatch[k] = j;
        k++;
      }
    }
    Log.debug("took: " + (System.nanoTime() - t0) / 1e9+" seconds.");

    // Now we can stitch together the final frame from the raw chunks that were
    // put into the store
    Log.info("Allocating and populated espc ...");
    t0 = System.nanoTime();
    long espc[] = new long[chunkSizes.length+1];
    int i=0;
    long sum=0;
    for (long s : chunkSizes) {
      espc[i++] = sum;
      sum+=s;
    }
    espc[espc.length-1] = sum;
    Log.debug("took: " + (System.nanoTime() - t0) / 1e9+" seconds.");
    assert(sum==ansN);

    Log.info("Allocating dummy vecs/chunks of the final frame ...");
    t0 = System.nanoTime();
    int numJoinCols = hasRite ? leftIndex._bytesUsed.length : 0;
    int numLeftCols = leftFrame.numCols();
    int numColsInResult = numLeftCols + rightFrame.numCols() - numJoinCols ;
    final byte[] types = new byte[numColsInResult];
    final String[][] doms = new String[numColsInResult][];
    final String[] names = new String[numColsInResult];
    for (int j=0; j<numLeftCols; j++) {
      types[j] = leftFrame.vec(j).get_type();
      doms[j] = leftFrame.domains()[j];
      names[j] = leftFrame.names()[j];
    }
    for (int j=0; j<rightFrame.numCols()-numJoinCols; j++) {
      types[numLeftCols + j] = rightFrame.vec(j+numJoinCols).get_type();
      doms[numLeftCols + j] = rightFrame.domains()[j+numJoinCols];
      names[numLeftCols + j] = rightFrame.names()[j+numJoinCols];
    }
    Key<Vec> key = Vec.newKey();
    Vec[] vecs = new Vec(key, Vec.ESPC.rowLayout(key, espc)).makeCons(numColsInResult, 0, doms, types);
    Log.debug("took: " + (System.nanoTime() - t0) / 1e9+" seconds.");

    Log.info("Finally stitch together by overwriting dummies ...");
    t0 = System.nanoTime();
    Frame fr = new Frame(names, vecs);
    ChunkStitcher ff = new ChunkStitcher(chunkSizes, chunkLeftMSB, chunkRightMSB, chunkBatch);
    ff.doAll(fr);
    Log.debug("took: " + (System.nanoTime() - t0) / 1e9+" seconds");

    //Merge.cleanUp();
    return fr;
  }

  public static List<SortCombine> gatherSameMSBRows(Frame leftFrame) {
    long t0 = System.nanoTime();
    List<SortCombine> bmList = new ArrayList<SortCombine>();
    Futures fs = new Futures();

    for (int leftMSB=0; leftMSB<=255; leftMSB++) {  // For each MSB, gather sorted rows with same MSB into one spot
      SingleThreadRadixOrder.OXHeader leftSortedOXHeader = DKV.getGet(getSortedOXHeaderKey(/*left=*/true, leftMSB));
      if (leftSortedOXHeader != null) {
        SortCombine bm = new SortCombine(new SortCombine.FFSB(leftFrame, leftMSB), leftSortedOXHeader);
        bmList.add(bm);
        fs.add(new RPC<>(SplitByMSBLocal.ownerOfMSB(leftMSB), bm).call());
      }
    }
    Log.debug("took: " + String.format("%.3f", (System.nanoTime() - t0) / 1e9)+" seconds.");
    Log.debug("Removing DKV keys of left index.  ... "); // finished gather the sorted rows per MSB, remove used objects
    t0 = System.nanoTime();
    Log.info("Sending BinaryMerge async RPC calls in a queue ... ");
    fs.blockForPending();
    Log.debug("took: " + (System.nanoTime() - t0) / 1e9+" seconds.");

    t0 = System.nanoTime();
    // now that we have collected sorted columns for each MSB, remove info that are no longer needed
    for (int msb=0; msb<256; msb++) {
      for (int isLeft=0; isLeft<2; isLeft++) {
        Key k = getSortedOXHeaderKey(isLeft!=0, msb);
        SingleThreadRadixOrder.OXHeader oxheader = DKV.getGet(k);
        DKV.remove(k);
        if (oxheader != null) {
          for (int b=0; b<oxheader._nBatch; ++b) {
            k = SplitByMSBLocal.getSortedOXbatchKey(isLeft!=0, msb, b);
            DKV.remove(k);
          }
        }
      }
    }
    Log.debug("took: " + (System.nanoTime() - t0)/1e9+" seconds.");
    return bmList;
  }

  public static class RemoveNAsTask extends MRTask<RemoveNAsTask> {

    private final int[] _columns;

    public RemoveNAsTask(int ... _columns) {
      this._columns = _columns;
    }

    private void copyRow(int row, Chunk[] cs, NewChunk[] ncs) {
      for (int i = 0; i < cs.length; ++i) {
        if (cs[i] instanceof CStrChunk) ncs[i].addStr(cs[i], row);
        else if (cs[i] instanceof C16Chunk) ncs[i].addUUID(cs[i], row);
        else if (cs[i].hasFloat()) ncs[i].addNum(cs[i].atd(row));
        else ncs[i].addNum(cs[i].at8(row), 0);
      }
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
      boolean noNA = true;
      for (int row = 0; row < cs[0]._len; ++row) {
        noNA = true;
        for (int col : _columns) {
          if (cs[col].isNA(row)) {
            noNA = false;
            break;
          }
        }
        if (noNA)
          copyRow(row, cs, ncs);
      }
    }
  }

  public static long allocateChunk(List<SortCombine> bmList, long chunkSizes[], int chunkLeftMSB[], 
                                   int chunkRightMSB[], int chunkBatch[]) {
    Log.info("Allocating and populating chunk info (e.g. size and batch number) ...");
    Long t0 = System.nanoTime();
    long ansN = 0;
    int numChunks = 0;
    for( SortCombine thisbm : bmList )
      if( thisbm._numRowsInResult > 0 ) {
        numChunks += thisbm._chunkSizes.length;
        ansN += thisbm._numRowsInResult;
      }
    chunkSizes = new long[numChunks];
    chunkLeftMSB = new int[numChunks];  // using too much space repeating the same value here, but, limited
    chunkRightMSB = new int[numChunks]; // leave it alone so as not to re-write chunkStitcher, fill with -1
    Arrays.fill(chunkRightMSB, -1);
    chunkBatch = new int[numChunks];
    int k = 0;
    for( SortCombine thisbm : bmList ) {
      if (thisbm._numRowsInResult == 0) continue;
      int thisChunkSizes[] = thisbm._chunkSizes;
      for (int j=0; j<thisChunkSizes.length; j++) {
        chunkSizes[k] = thisChunkSizes[j];
        chunkLeftMSB [k] = thisbm._leftSB._msb;
        chunkBatch[k] = j;
        k++;
      }
    }
    Log.debug("took: " + (System.nanoTime() - t0) / 1e9 +" seconds.");
    return ansN;
  }
  
  public static Frame allocatePopulateChunk(List<SortCombine> bmList, Frame leftFrame, long ansN, long chunkSizes[], 
                                            int chunkLeftMSB[], int chunkRightMSB[], int chunkBatch[]) {
    // Now we can stitch together the final frame from the raw chunks that were
    // put into the store
    Log.info("Allocating and populated espc ...");
    long t0 = System.nanoTime();
    long espc[] = new long[chunkSizes.length+1];
    int i=0;
    long sum=0;
    for (long s : chunkSizes) {
      espc[i++] = sum;
      sum+=s;
    }
    espc[espc.length-1] = sum;
    Log.debug("took: " + (System.nanoTime() - t0) / 1e9+" seconds");
    assert(sum==ansN);

    Log.info("Allocating dummy vecs/chunks of the final frame ...");
    t0 = System.nanoTime();
    int numLeftCols = leftFrame.numCols();
    int numColsInResult = numLeftCols;
    final byte[] types = new byte[numColsInResult];
    final String[][] doms = new String[numColsInResult][];
    final String[] names = new String[numColsInResult];
    for (int j=0; j<numLeftCols; j++) {
      types[j] = leftFrame.vec(j).get_type();
      doms[j] = leftFrame.domains()[j];
      names[j] = leftFrame.names()[j];
    }

    Key<Vec> key = Vec.newKey();
    Vec[] vecs = new Vec(key, Vec.ESPC.rowLayout(key, espc)).makeCons(numColsInResult, 0, doms, types);
    Log.debug("took: " + (System.nanoTime() - t0) / 1e9+" seconds");

    Log.info("Finally stitch together by overwriting dummies ...");
    t0 = System.nanoTime();
    Frame fr = new Frame(names, vecs);
    ChunkStitcher ff = new ChunkStitcher(chunkSizes, chunkLeftMSB, chunkRightMSB, chunkBatch);
    ff.doAll(fr);
    Log.debug("took: " + (System.nanoTime() - t0) / 1e9+" seconds.");
    return fr;
  }

  public static Frame sortOnly(final Frame leftFrame, final int leftCols[], int[][] id_maps, int[] ascendingL) {
    createIndex(true, leftFrame, leftCols, id_maps, ascendingL);  // sort the columns.
    Log.info("Making BinaryMerge RPC calls ... ");
    List<SortCombine> bmList = gatherSameMSBRows(leftFrame); // For each MSB, gather sorted rows with same MSB into one spot
    Log.info("Allocating and populating chunk info (e.g. size and batch number) ...");
    Long t0 = System.nanoTime();
    long ansN = 0;
    int numChunks = 0;
    for (SortCombine thisbm : bmList)
      if (thisbm._numRowsInResult > 0) {
        numChunks += thisbm._chunkSizes.length;
        ansN += thisbm._numRowsInResult;
      }
    long chunkSizes[] = new long[numChunks];
    int chunkLeftMSB[] = new int[numChunks];  // using too much space repeating the same value here, but, limited
    int chunkRightMSB[] = new int[numChunks]; // leave it alone so as not to re-write chunkStitcher, fill with -1
    Arrays.fill(chunkRightMSB, -1);
    int chunkBatch[] = new int[numChunks];
    int k = 0;
    for (SortCombine thisbm : bmList) {
      if (thisbm._numRowsInResult == 0) continue;
      int thisChunkSizes[] = thisbm._chunkSizes;
      for (int j = 0; j < thisChunkSizes.length; j++) {
        chunkSizes[k] = thisChunkSizes[j];
        chunkLeftMSB[k] = thisbm._leftSB._msb;
        chunkBatch[k] = j;
        k++;
      }
    }
    Log.debug("took: " + (System.nanoTime() - t0) / 1e9 + " seconds.");
    long finalRowNumber = allocateChunk(bmList, chunkSizes, chunkLeftMSB, chunkRightMSB, chunkBatch);
    Log.info("Populate chunks and form final sorted frame ...");
    return allocatePopulateChunk(bmList, leftFrame, finalRowNumber, chunkSizes, chunkLeftMSB, chunkRightMSB, chunkBatch);
  }
  
  private static RadixOrder createIndex(boolean isLeft, Frame fr, int[] cols, int[][] id_maps, int[] ascending) {
    Log.info("Creating "+(isLeft ? "left" : "right")+" index ...");
    long t0 = System.nanoTime();
    RadixOrder idxTask = new RadixOrder(fr, isLeft, cols, id_maps, ascending);
    H2O.submitTask(idxTask);    // each of those launches an MRTask
    idxTask.join(); 
    Log.debug("*** Creating "+(isLeft ? "left" : "right")+" index took: " + (System.nanoTime() - t0) / 1e9 + " seconds ***");
    return idxTask;
  }

  static class ChunkStitcher extends MRTask<ChunkStitcher> {
    final long _chunkSizes[];
    final int  _chunkLeftMSB[];
    final int  _chunkRightMSB[];
    final int  _chunkBatch[];
    ChunkStitcher(long[] chunkSizes,
                  int[]  chunkLeftMSB,
                  int[]  chunkRightMSB,
                  int[]  chunkBatch
    ) {
      _chunkSizes   = chunkSizes;
      _chunkLeftMSB = chunkLeftMSB;
      _chunkRightMSB= chunkRightMSB;
      _chunkBatch   = chunkBatch;
    }
    @Override
    public void map(Chunk[] cs) {
      int chkIdx = cs[0].cidx();
      Futures fs = new Futures();
      for (int i=0;i<cs.length;++i) {
        Key destKey = cs[i].vec().chunkKey(chkIdx);
        assert(cs[i].len() == _chunkSizes[chkIdx]);
        Key k = BinaryMerge.getKeyForMSBComboPerCol(_chunkLeftMSB[chkIdx], _chunkRightMSB[chkIdx], i, _chunkBatch[chkIdx]);
        Chunk ck = DKV.getGet(k);
        DKV.put(destKey, ck, fs, /*don't cache*/true);
        DKV.remove(k);
      }
      fs.blockForPending();
    }
  }
}

