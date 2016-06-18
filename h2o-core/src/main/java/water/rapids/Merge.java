package water.rapids;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MRUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static water.rapids.SingleThreadRadixOrder.getSortedOXHeaderKey;

public class Merge {

  // Hack to cleanup helpers made during merge
  // TODO: Keep radix order (Keys) around as hidden objects for a given frame and key column
  // TODO: delete as we now delete keys on the fly as soon as we getGet them ...
  /*static void cleanUp() {
    new MRTask() {
      protected void setupLocal() {
        Object [] kvs = H2O.STORE.raw_array();
        for(int i = 2; i < kvs.length; i+= 2){
          Object ok = kvs[i];
          if( !(ok instanceof Key  ) ) continue; // Ignore tombstones and Primes and null's
          Key key = (Key )ok;
          if(!key.home())continue;
          String st = key.toString();
          if (st.contains("__radix_order__") || st.contains("__binary_merge__")) {
            DKV.remove(key);
          }
        }
      }
    }.doAllNodes();
  }*/

  static void waitForSignalFromMatt() {
    System.out.println("waiting at the spot");
    File f = new File("/home/mdowle/GOFLAG");
    while (true) {
      System.out.println("Waiting for GOFLAG ...");
      if (f.exists()) {
        f.delete();
        System.out.println("GOFLAG seen, deleted and moved on");
        break;
      }
      try { Thread.sleep(1000); } catch (Exception ignore) {}
    }
  }

  // single-threaded driver logic
  static Frame merge(final Frame leftFrame, final Frame rightFrame, final int leftCols[], final int rightCols[], boolean allLeft, int[][] id_maps) {

    // each of those launches an MRTask
    System.out.println("\nCreating left index ...");
    long t0 = System.nanoTime();
    RadixOrder leftIndex;

    // map missing levels to -1 (rather than increasing slots after the end) for now to save a deep branch later
    for (int i=0; i<id_maps.length; i++) {
      if (id_maps[i] == null) continue;
      assert id_maps[i].length == leftFrame.vec(leftCols[i]).max()+1;
      int right_max = (int)rightFrame.vec(rightCols[i]).max();
      for (int j=0; j<id_maps[i].length; j++) {
        assert id_maps[i][j] >= 0;
        if (id_maps[i][j] > right_max) id_maps[i][j] = -1;
      }
    }

    /* for (int s=0; s<5; s++) {
      try { Thread.sleep(1000); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
      System.gc();
    } */

    ///waitForSignalFromMatt();

    H2O.H2OCountedCompleter left = H2O.submitTask(leftIndex = new RadixOrder(leftFrame, /*isLeft=*/true, leftCols, id_maps));
    left.join(); // Running 3 consecutive times on an idle cluster showed that running left and right in parallel was
                 // a little slower (97s) than one by one (89s).  TODO: retest in future
    System.out.println("***\n*** Creating left index took: " + (System.nanoTime() - t0) / 1e9 + "\n***\n");

    /* for (int s=0; s<5; s++) {
      try { Thread.sleep(1000); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
      System.gc();
    } */

    ///waitForSignalFromMatt();

    System.out.println("\nCreating right index ...");
    t0 = System.nanoTime();
    RadixOrder rightIndex;
    H2O.H2OCountedCompleter right = H2O.submitTask(rightIndex = new RadixOrder(rightFrame, /*isLeft=*/false, rightCols, null));
    right.join();
    System.out.println("***\n*** Creating right index took: " + (System.nanoTime() - t0) / 1e9 + "\n***\n");
    /*for (int s=0; s<5; s++) {
      try { Thread.sleep(1000); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
      System.gc();
    }*/

    // TODO: start merging before all indexes had been created. Use callback?

    // TODO: Tomas showed this method which takes 'true' argument to H2OCountedCompleter directly.  Not sure how that
    // relates to new of RadixOrder which itself extends H2OCountedCompleter.  That true argument isn't available that way.
    // H2O.submitTask(new H2O.H2OCountedCompleter(true) {   // true just to bump priority and prevent deadlock (no different to speed, in theory - Tomas) in case
    // this Merge ever called from within another counted completer
    // @Override
    // protected void compute2() {
    //   RadixOrder.compute(leftFrame, /*left=*/true, leftCols);  // when RadixOrder had just a static compute() method
    //   tryComplete();
    // }

    long ansN = 0;
    int numChunks = 0;

    System.out.println("Left base[0]: "+leftIndex._base[0]);
    System.out.println("Left shift: "+leftIndex._shift[0]);
    System.out.println("Right base[0]: "+rightIndex._base[0]);
    System.out.println("Right shift: "+rightIndex._shift[0]);

    System.out.print("Making BinaryMerge RPC calls ... ");
    t0 = System.nanoTime();
    List<RPC> bmList = new ArrayList<>();
    int leftShift = leftIndex._shift[0];
    int rightShift = rightIndex._shift[0];

    long leftMSBfrom = (rightIndex._base[0] - leftIndex._base[0]) >> leftShift;  // which leftMSB does the overlap start

    // deal with the left range below the right minimum, if any
    if (leftIndex._base[0] < rightIndex._base[0]) {
      // deal with the range of the left below the start of the right, if any
      assert leftMSBfrom >= 0;
      if (leftMSBfrom>255) {
        // The left range ends before the right range starts.  So every left row is a no-match to the right
        leftMSBfrom = 256;  // so that the loop below runs for all MSBs (0-255) to fetch the left rows only
      }
      // run the merge for the whole lefts that end before the first right.  The overlapping one with the right base is dealt with inside BinaryMerge (if _allLeft)
      if (allLeft) for (int leftMSB=0; leftMSB<leftMSBfrom; leftMSB++) {
        bmList.add(new RPC<>(SplitByMSBLocal.ownerOfMSB(0), new BinaryMerge(
          leftFrame, rightFrame, leftMSB, /*rightMSB*/-1, leftShift, rightShift, leftIndex._bytesUsed, rightIndex._bytesUsed, leftIndex._base, rightIndex._base, allLeft
        )));
      }
    } else {
      // completely ignore right MSBs below the left base
      assert leftMSBfrom <= 0;
      leftMSBfrom = 0;
    }

    long leftMSBto = (rightIndex._base[0] + (256L<<rightShift) - 1 - leftIndex._base[0]) >> leftShift;
    // -1 because the 256L<<rightShift is one after the max extent.  No need for +1 for NA here because, as for leftMSBfrom above, the NA spot is on both sides

    // deal with the left range above the right maximum, if any
    if ((leftIndex._base[0] + (256L<<leftShift)) > (rightIndex._base[0] + (256L<<rightShift))) {
      assert leftMSBto <= 255;
      if (leftMSBto<0) {
        // The left range starts after the right range ends.  So every left row is a no-match to the right
        leftMSBto = -1;  // all MSBs (0-255) need to fetch the left rows only
      }
      // run the merge for the whole lefts that start after the last right
      if (allLeft) for (int leftMSB=(int)leftMSBto+1; leftMSB<=255; leftMSB++) {
        bmList.add(new RPC<>(SplitByMSBLocal.ownerOfMSB(0), new BinaryMerge(
          leftFrame, rightFrame, leftMSB, /*rightMSB*/-1, leftShift, rightShift, leftIndex._bytesUsed, rightIndex._bytesUsed, leftIndex._base, rightIndex._base, allLeft
        )));
      }
    } else {
      // completely ignore right MSBs after the right peak
      assert leftMSBto >= 255;
      leftMSBto = 255;
    }

    System.out.print("(" + bmList.size() + " left outer outside range) ... ");

    // the overlapped region; i.e. between [ max(leftMin,rightMin), min(leftMax, rightMax) ]
    for (int leftMSB=(int)leftMSBfrom; leftMSB<=leftMSBto; leftMSB++) {

      assert leftMSB >= 0;
      assert leftMSB <= 255;

      // calculate the key values at the bin extents:  [leftFrom,leftTo] in terms of keys
      long leftFrom = ((long)leftMSB << leftShift) -1 + leftIndex._base[0];  // -1 to cater for leading NA spot
      long leftTo = (((long)leftMSB+1) << leftShift) + leftIndex._base[0] - 1 - 1;  // -1 for leading NA spot and another -1 to get last of previous bin

      // which right bins do these left extents occur in (could span multiple, and fall in the middle)
      int rightMSBfrom = (int)((leftFrom - rightIndex._base[0] + 1) >> rightShift);   // +1 again for the leading NA spot
      int rightMSBto = (int)((leftTo - rightIndex._base[0] + 1) >> rightShift);

      if (rightMSBfrom < 0) rightMSBfrom = 0;   // the non-matching part of this region will have been dealt with above when allLeft==true
      assert rightMSBfrom <= 255;
      if (rightMSBto > 255) rightMSBto = 255;
      assert rightMSBto >= rightMSBfrom;

      for (int rightMSB=rightMSBfrom; rightMSB<=rightMSBto; rightMSB++) {

        H2ONode node = SplitByMSBLocal.ownerOfMSB(rightMSB);
        // TODO: choose the bigger side to execute on (where that side of index already is) to minimize transfer

        // System.out.println("Calling BinaryMerge for " + leftMSB + " " + rightMSB + " on node " + node.index());

        // within BinaryMerge it will recalculate the extents in terms of keys and bsearch for them within the (then local) both sides
        bmList.add(new RPC<>(node,
          new BinaryMerge(leftFrame, rightFrame,
                  leftMSB, rightMSB,
                  leftShift, rightShift,
                  leftIndex._bytesUsed,   // field sizes for each column in the key
                  rightIndex._bytesUsed,
                  leftIndex._base,
                  rightIndex._base,
                  allLeft
          )
        ));
      }
    }
    System.out.println("took: " + String.format("%.3f", (System.nanoTime() - t0) / 1e9));

    int queueSize = bmList.size();
    // Now that gc issues resolved, it seems ok to send them all at once.
    // No longer floods the cluster it seems
    // TODO: can remove manual queuing now and save risk of waitMS slowing unnecessarily
    System.out.println("Dispatching in queue size of "+queueSize+". H2O.NUMCPUS="+H2O.NUMCPUS + " H2O.CLOUD.size()="+H2O.CLOUD.size());
    
    t0 = System.nanoTime();
    System.out.println("Sending "+bmList.size()+" BinaryMerge async RPC calls in a queue of " + queueSize + " ... ");

    int queue[] = new int[queueSize];
    BinaryMerge bmResults[] = new BinaryMerge[bmList.size()];

    int nextItem;
    for (nextItem=0; nextItem<queueSize; nextItem++) {
      queue[nextItem] = nextItem;
      bmList.get(nextItem).call();  // async
    }
    int leftOnQueue = queueSize;
    int waitMS = 50;  // 0.05 second for fast runs like 1E8 on 1 node
    while (leftOnQueue > 0) {
      try {
        Thread.sleep(waitMS);
      } catch(InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
      // System.out.println("Sweeping queue. leftOnQueue="+leftOnQueue+" queueSize="+queueSize+" ...");
      int doneInSweep = 0;
      for (int q=0; q<queueSize; q++) {
        int thisBM = queue[q];
        if (thisBM >= 0 && bmList.get(thisBM).isDone()) {
          BinaryMerge thisbm;
          bmResults[thisBM] = thisbm = (BinaryMerge)bmList.get(thisBM).get();
          leftOnQueue--;
          doneInSweep++;
          if (thisbm._numRowsInResult > 0) {
            System.out.print(String.format("%3d",queue[q]) + ":");
            for (int t=0; t<20; t++) System.out.print(String.format("%.2f ", thisbm._timings[t]));
            System.out.println();
            numChunks += thisbm._chunkSizes.length;
            ansN += thisbm._numRowsInResult;
          }
          queue[q] = -1;  // clear the slot
          if (nextItem < bmList.size()) {
            bmList.get(nextItem).call();   // call next one
            queue[q] = nextItem++;         // put on queue so we can sweep
            leftOnQueue++;
          }
        }
      }
      if (doneInSweep == 0) waitMS = Math.min(1000, waitMS*2);  // if last sweep caught none, then double wait time to avoid cost of sweeping
      else {
        // When tracing memory going one-by-one
        /*for (int s=0; s<3; s++) {
          try { Thread.sleep(1000); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
          System.gc();
        }*/
      }
    }
    System.out.println("took: " + (System.nanoTime() - t0) / 1e9);


    System.out.print("Removing DKV keys of left and right index.  ... ");
    // TODO: In future we won't delete but rather persist them as index on the table
    // Explicitly deleting here (rather than Arno's cleanUp) to reveal if we're not removing keys early enough elsewhere
    t0 = System.nanoTime();
    for (int msb=0; msb<256; msb++) {
      for (int isLeft=0; isLeft<2; isLeft++) {
        Key k = getSortedOXHeaderKey(isLeft==0 ? false : true, msb);
        SingleThreadRadixOrder.OXHeader oxheader = DKV.getGet(k);
        DKV.remove(k);
        if (oxheader != null) {
          for (int b=0; b<oxheader._nBatch; ++b) {
            k = SplitByMSBLocal.getSortedOXbatchKey(isLeft==0 ? false : true, msb, b);
            DKV.remove(k);
          }
        }
      }
    }
    System.out.println("took: " + (System.nanoTime() - t0)/1e9);

    /*System.out.println("Waiting for BinaryMerge RPCs to finish ... ");
    t0 = System.nanoTime();
    BinaryMerge bmResults[] = new BinaryMerge[bmList.size()];   // all the results were being collected on one node here?  No. bmResults are small.
    int i=0;
    for (RPC rpc : bmList) {
      System.out.print(String.format("%4d: ", i));
      BinaryMerge thisbm;
      bmResults[i++] = thisbm = (BinaryMerge)rpc.get(); //block
      for (int t=0; t<12; t++) System.out.print(String.format("%5.2f ", thisbm._timings[t]));
      System.out.println();
      if (thisbm._numRowsInResult == 0) continue;
      numChunks += thisbm._chunkSizes.length;
      ansN += thisbm._numRowsInResult;
      // There is no BinaryMerge[i] = null incrementally.  No wonder it is blowing up!
    }
    System.out.println("\nBinaryMerge RPCs took: " + (System.nanoTime() - t0) / 1e9);
    assert(i == bmList.size());
*/
    //return new Frame();
    System.out.print("Allocating and populating chunk info (e.g. size and batch number) ...");
    t0 = System.nanoTime();
    long chunkSizes[] = new long[numChunks];
    int chunkLeftMSB[] = new int[numChunks];  // using too much space repeating the same value here, but, limited
    int chunkRightMSB[] = new int[numChunks];
    int chunkBatch[] = new int[numChunks];
    int k = 0;
    for (int i=0; i<bmList.size(); i++) {
      BinaryMerge thisbm = bmResults[i];
      if (thisbm._numRowsInResult == 0) continue;
      int thisChunkSizes[] = thisbm._chunkSizes;
      for (int j=0; j<thisChunkSizes.length; j++) {
        chunkSizes[k] = thisChunkSizes[j];
        chunkLeftMSB[k] = thisbm._leftMSB;
        chunkRightMSB[k] = thisbm._rightMSB;
        chunkBatch[k] = j;
        k++;
      }
    }
    System.out.println("took: " + (System.nanoTime() - t0) / 1e9);

    // Now we can stitch together the final frame from the raw chunks that were put into the store

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
    int numJoinCols = leftIndex._bytesUsed.length;
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
    Key key = Vec.newKey();
    Vec[] vecs = new Vec(key, Vec.ESPC.rowLayout(key, espc)).makeCons(numColsInResult, 0, doms, types);
    // to delete ... String[] names = ArrayUtils.append(leftFrame.names(), ArrayUtils.select(rightFrame.names(),  ArrayUtils.seq(numJoinCols, rightFrame.numCols() - 1)));
    System.out.println("took: " + (System.nanoTime() - t0) / 1e9);

    System.out.print("Finally stitch together by overwriting dummies ...");
    t0 = System.nanoTime();
    Frame fr = new Frame(names, vecs);
    ChunkStitcher ff = new ChunkStitcher(/*leftFrame, rightFrame,*/ chunkSizes, chunkLeftMSB, chunkRightMSB, chunkBatch);
    ff.doAll(fr);
    System.out.println("took: " + (System.nanoTime() - t0) / 1e9);

    //Merge.cleanUp();
    return fr;
  }

  static class ChunkStitcher extends MRTask<ChunkStitcher> {
    //final Frame _leftFrame;
    //final Frame _rightFrame;
    final long _chunkSizes[];
    final int _chunkLeftMSB[];
    final int _chunkRightMSB[];
    final int _chunkBatch[];
    public ChunkStitcher(//Frame leftFrame,
                         //Frame rightFrame,
                         long[] chunkSizes,
                         int[] chunkLeftMSB,
                         int[] chunkRightMSB,
                         int[] chunkBatch
    ) {
      //_leftFrame = leftFrame;
      //_rightFrame = rightFrame;
      _chunkSizes = chunkSizes;
      _chunkLeftMSB = chunkLeftMSB;
      _chunkRightMSB = chunkRightMSB;
      _chunkBatch = chunkBatch;
    }
    @Override
    public void map(Chunk[] cs) {
      int chkIdx = cs[0].cidx();
      Futures fs = new Futures();
      for (int i=0;i<cs.length;++i) {
        Key destKey = cs[i].vec().chunkKey(chkIdx);
        assert(cs[i].len() == _chunkSizes[chkIdx]);
        Key k = BinaryMerge.getKeyForMSBComboPerCol(/*_leftFrame, _rightFrame,*/ _chunkLeftMSB[chkIdx], _chunkRightMSB[chkIdx], i, _chunkBatch[chkIdx]);
        Chunk ck = DKV.getGet(k);
        DKV.put(destKey, ck, fs, /*don't cache*/true);
        DKV.remove(k);
      }
      fs.blockForPending();
    }
  }
}

