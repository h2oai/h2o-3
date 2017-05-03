package water.rapids;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.math.BigInteger;
import java.util.ArrayList;

import static water.rapids.SingleThreadRadixOrder.getSortedOXHeaderKey;

public class Merge {

  // Radix-sort a Frame using the given columns as keys.
  // This is a fully distributed and parallel sort.
  // It is not currently an in-place sort, so the data is doubled and a sorted copy is returned.
  public static Frame sort( final Frame fr, int[] cols ) {
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

    return Merge.merge(fr, new Frame(new Vec[0]), cols, new int[0], true/*allLeft*/, id_maps);
  }

  // single-threaded driver logic.  Merge left and right frames based on common columns.
  public static Frame merge(final Frame leftFrame, final Frame riteFrame, final int leftCols[], final int riteCols[], boolean allLeft, int[][] id_maps) {
    final boolean hasRite = riteCols.length > 0;

    // map missing levels to -1 (rather than increasing slots after the end)
    // for now to save a deep branch later
    for (int i=0; i<id_maps.length; i++) {
      if (id_maps[i] == null) continue;
      assert id_maps[i].length >= leftFrame.vec(leftCols[i]).max()+1;
      if( !hasRite ) continue;
      int right_max = (int)riteFrame.vec(riteCols[i]).max();
      for (int j=0; j<id_maps[i].length; j++) {
        assert id_maps[i][j] >= 0;
        if (id_maps[i][j] > right_max) id_maps[i][j] = -1;
      }
    }

    // Running 3 consecutive times on an idle cluster showed that running left
    // and right in parallel was a little slower (97s) than one by one (89s).
    // TODO: retest in future
    RadixOrder leftIndex = createIndex(true ,leftFrame,leftCols,id_maps);
    RadixOrder riteIndex = createIndex(false,riteFrame,riteCols,id_maps);

    // TODO: start merging before all indexes had been created. Use callback?

    System.out.print("Making BinaryMerge RPC calls ... ");
    long t0 = System.nanoTime();
    ArrayList<BinaryMerge> bmList = new ArrayList<>();
    Futures fs = new Futures();
    final int leftShift = leftIndex._shift[0];
    final long leftBase = leftIndex._base[0];
    final int riteShift = hasRite ? riteIndex._shift[0] : -1;
    final long riteBase = hasRite ? riteIndex._base [0] : leftBase;

    // initialize for double columns, may not be used....
    final BigInteger leftBaseD = leftIndex._isNumeric[0] ? leftIndex._baseD[0] : BigInteger.ZERO;
    final BigInteger riteBaseD = hasRite ? (riteIndex._isNumeric[0] ? riteIndex._baseD[0] : BigInteger.ZERO) : leftBaseD;

    long leftMSBfrom = leftIndex._isNumeric[0]?riteBaseD.subtract(leftBaseD).shiftRight(leftShift).longValue():(riteBase - leftBase) >> leftShift;
    boolean riteBaseExceedsleftBase=leftIndex._isNumeric[0]?riteBaseD.compareTo(leftBaseD)>0:(riteBase>leftBase);
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
                leftIndex._bytesUsed, leftIndex._base, leftIndex._baseD,
                leftIndex._isNumeric), new BinaryMerge.FFSB(riteFrame,/*rightMSB*/-1, riteShift,
                riteIndex._bytesUsed, riteIndex._base, riteIndex._baseD, riteIndex._isNumeric),
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
    long leftMSBto = (leftIndex._isNumeric[0])?riteBaseD.add(rightS).subtract(BigInteger.ONE).subtract(leftBaseD).shiftRight(leftShift).longValue():
            ((riteBase + (256L<<riteShift) - 1 - leftBase) >> leftShift);
    // -1 because the 256L<<riteShift is one after the max extent.  
    // No need -for +1 for NA here because, as for leftMSBfrom above, the NA spot is on -both sides

    // deal with the left range above the right maximum, if any.  For doubles, -1 from shift to avoid negative outcome
    boolean leftRangeAboveRightMax = (leftIndex._isNumeric[0])?
            (leftBaseD.add(BigInteger.valueOf(256L<<leftShift)).compareTo(riteBaseD.add(rightS)) >= 0) :
            (leftBase + (256L<<leftShift)) > (riteBase + (256L<<riteShift));

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
                  leftIndex._bytesUsed,leftIndex._base, leftIndex._baseD,  leftIndex._isNumeric),
                                           new BinaryMerge.FFSB(riteFrame,/*rightMSB*/-1,riteShift,
                                                   riteIndex._bytesUsed,riteIndex._base, riteIndex._baseD,
                                                   riteIndex._isNumeric),
                                           true);
          bmList.add(bm);
          fs.add(new RPC<>(SplitByMSBLocal.ownerOfMSB(leftMSB), bm).call());
      }
    } else {
      // completely ignore right MSBs after the right peak
      assert leftMSBto >= 255;
      leftMSBto = 255;
    }

    // the overlapped region; i.e. between [ max(leftMin,rightMin), min(leftMax, rightMax) ]
    for (int leftMSB=(int)leftMSBfrom; leftMSB<=leftMSBto; leftMSB++) {

      assert leftMSB >= 0;
      assert leftMSB <= 255;

      // calculate the key values at the bin extents:  [leftFrom,leftTo] in terms of keys
      long leftFrom= leftIndex._isNumeric[0]?(((long)leftMSB  ) << leftShift) -1 + leftBaseD.longValue():(((long)leftMSB  ) << leftShift) -1 + leftBase  ;  // -1 for leading NA spot
      long leftTo  = leftIndex._isNumeric[0]?(((long)leftMSB+1) << leftShift) -1 + leftBaseD.longValue()-1:(((long)leftMSB+1) << leftShift) -1 + leftBase-1;  // -1 for leading NA spot and another -1 to get last of previous bin

      // which right bins do these left extents occur in (could span multiple, and fall in the middle)
      int rightMSBfrom = riteIndex._isNumeric[0]?(int)((leftFrom - riteBaseD.longValue() + 1) >> riteShift):(int)((leftFrom - riteBase + 1) >> riteShift);   // +1 again for the leading NA spot
      int rightMSBto   = riteIndex._isNumeric[0]?(int)((leftTo   - riteBaseD.longValue() + 1) >> riteShift):(int)((leftTo   - riteBase + 1) >> riteShift);

      // the non-matching part of this region will have been dealt with above when allLeft==true
      if (rightMSBfrom < 0) rightMSBfrom = 0;
      assert rightMSBfrom <= 255;
      if (rightMSBto > 255) rightMSBto = 255;
      assert rightMSBto >= rightMSBfrom;

      for (int rightMSB=rightMSBfrom; rightMSB<=rightMSBto; rightMSB++) {
        BinaryMerge bm = new BinaryMerge(new BinaryMerge.FFSB(leftFrame, leftMSB,leftShift,leftIndex._bytesUsed,leftIndex._base,leftIndex._baseD, leftIndex._isNumeric),
                                         new BinaryMerge.FFSB(riteFrame,rightMSB,riteShift,riteIndex._bytesUsed,riteIndex._base,riteIndex._baseD,riteIndex._isNumeric),
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
    System.out.println("took: " + String.format("%.3f", (System.nanoTime() - t0) / 1e9));

    t0 = System.nanoTime();
    System.out.println("Sending BinaryMerge async RPC calls in a queue ... ");
    fs.blockForPending();
    System.out.println("took: " + (System.nanoTime() - t0) / 1e9);


    System.out.print("Removing DKV keys of left and right index.  ... ");
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
    System.out.println("took: " + (System.nanoTime() - t0)/1e9);

    System.out.print("Allocating and populating chunk info (e.g. size and batch number) ...");
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
    System.out.println("took: " + (System.nanoTime() - t0) / 1e9);

    // Now we can stitch together the final frame from the raw chunks that were
    // put into the store
    System.out.print("Allocating and populated espc ...");
    t0 = System.nanoTime();
    long espc[] = new long[chunkSizes.length+1];
    int i=0;
    long sum=0;
    for (long s : chunkSizes) {
      espc[i++] = sum;
      sum+=s;
    }
    espc[espc.length-1] = sum;
    System.out.println("took: " + (System.nanoTime() - t0) / 1e9);
    assert(sum==ansN);

    System.out.print("Allocating dummy vecs/chunks of the final frame ...");
    t0 = System.nanoTime();
    int numJoinCols = hasRite ? leftIndex._bytesUsed.length : 0;
    int numLeftCols = leftFrame.numCols();
    int numColsInResult = numLeftCols + riteFrame.numCols() - numJoinCols ;
    final byte[] types = new byte[numColsInResult];
    final String[][] doms = new String[numColsInResult][];
    final String[] names = new String[numColsInResult];
    for (int j=0; j<numLeftCols; j++) {
      types[j] = leftFrame.vec(j).get_type();
      doms[j] = leftFrame.domains()[j];
      names[j] = leftFrame.names()[j];
    }
    for (int j=0; j<riteFrame.numCols()-numJoinCols; j++) {
      types[numLeftCols + j] = riteFrame.vec(j+numJoinCols).get_type();
      doms[numLeftCols + j] = riteFrame.domains()[j+numJoinCols];
      names[numLeftCols + j] = riteFrame.names()[j+numJoinCols];
    }
    Key<Vec> key = Vec.newKey();
    Vec[] vecs = new Vec(key, Vec.ESPC.rowLayout(key, espc)).makeCons(numColsInResult, 0, doms, types);
    System.out.println("took: " + (System.nanoTime() - t0) / 1e9);

    System.out.print("Finally stitch together by overwriting dummies ...");
    t0 = System.nanoTime();
    Frame fr = new Frame(names, vecs);
    ChunkStitcher ff = new ChunkStitcher(chunkSizes, chunkLeftMSB, chunkRightMSB, chunkBatch);
    ff.doAll(fr);
    System.out.println("took: " + (System.nanoTime() - t0) / 1e9);

    //Merge.cleanUp();
    return fr;
  }

  private static RadixOrder createIndex(boolean isLeft, Frame fr, int[] cols, int[][] id_maps) {
    System.out.println("\nCreating "+(isLeft ? "left" : "right")+" index ...");
    long t0 = System.nanoTime();
    RadixOrder idxTask = new RadixOrder(fr, isLeft, cols, id_maps);
    H2O.submitTask(idxTask);    // each of those launches an MRTask
    idxTask.join(); 
    System.out.println("***\n*** Creating "+(isLeft ? "left" : "right")+" index took: " + (System.nanoTime() - t0) / 1e9 + "\n***\n");
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

