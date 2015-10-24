package water.rapids;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.util.ArrayList;
import java.util.List;

public class Merge {

  // single-threaded driver logic
  static Frame merge(Frame leftFrame, Frame rightFrame, int leftCols[], int rightCols[]) {

    // each of those launches an MRTask
    RadixOrder leftIndex = new RadixOrder(leftFrame, leftCols);
    RadixOrder rightIndex = new RadixOrder(rightFrame, rightCols);

    // Align MSB locations between the two keys
    int bitShift = rightIndex._biggestBit[0] - leftIndex._biggestBit[0];
    int leftExtent = 256, rightExtent = 1;
    if (bitShift < 0) {
      // The biggest keys in left table are larger than the biggest in right table
      // Therefore those biggest ones don't have a match and we can instantly ignore them
      // The only msb's that can match are the smallest in left table ...
      leftExtent >>= -bitShift;
      // and those could join to multiple msb in the right table, one-to-many ...
      rightExtent <<= -bitShift;
    }
    // else if bitShift > 0
    //   The biggest keys in right table are larger than the largest in left table
    //   The msb values in left table need to be reduced in magnitude and will then join to the smallest of the right key's msb values
    //   Many left msb might join to the same (smaller) right msb
    //   Leave leftExtent at 256 and rightExtent at 1.
    //   The positive bitShift will reduce jbase below to common right msb's,  many-to-one
    // else bitShift == 0
    //   We hope most common case. Common width keys (e.g. ids, codes, enums, integers, etc) both sides over similar range
    //   Left msb will match exactly to right msb one-to-one, without any alignment needed.


    List<RPC> bmList = new ArrayList<>();
    for (int leftMSB =0; leftMSB <leftExtent; leftMSB++) { // each of left msb values.  TO DO: go parallel
//      long leftLen = leftIndex._MSBhist[i];
//      if (leftLen > 0) {
      int rightMSBBase = leftMSB >> bitShift;  // could be positive or negative, or most commonly and ideally bitShift==0
      for (int k=0; k<rightExtent; k++) {
        int rightMSB = rightMSBBase +k;
//          long rightLen = rightIndex._MSBhist[j];
//          if (rightLen > 0) {
        //System.out.print(i + " left " + lenx + " => right " + leny);
        // TO DO: when go distributed, move the smaller of lenx and leny to the other one's node.
        //        if 256 are distributed across 10 nodes in order with 1-25 on node 1, 26-50 on node 2 etc, then most already will be on same node.
        H2ONode leftNode = MoveByFirstByte.ownerOfMSB(leftMSB);
        H2ONode rightNode = MoveByFirstByte.ownerOfMSB(rightMSB);
        //if (leftMSB!=73 || rightMSB!=73) continue;
        //Log.info("Calling BinaryMerge for " + leftMSB + " " + rightMSB);
        RPC bm = new RPC<>(rightNode,
                new BinaryMerge(leftFrame, rightFrame,
                        leftMSB, rightMSB,
                        //leftNode.index(), //convention - right frame is local, but left frame is potentially remote
                        leftIndex._bytesUsed,   // field sizes for each column in the key
                        rightIndex._bytesUsed
                )
        );
        bmList.add(bm);
        bm.call(); //async
      }
    }

    long ansN = 0;
    int numChunks = 0;
    BinaryMerge bmResults[] = new BinaryMerge[bmList.size()];
    int i=0;
    for (RPC rpc : bmList) {
      BinaryMerge thisbm;
      bmResults[i++] = thisbm = (BinaryMerge)rpc.get(); //block
      if (thisbm._ansN == 0) continue;
      numChunks += thisbm._chunkSizes.length;
      ansN += thisbm._ansN;
    }
    assert(i == bmList.size());

    long chunkSizes[] = new long[numChunks];
    int chunkLeftMSB[] = new int[numChunks];  // using too much space repeating the same value here, but, limited
    int chunkRightMSB[] = new int[numChunks];
    int chunkBatch[] = new int[numChunks];
    int k = 0;
    for (i=0; i<bmList.size(); i++) {
      BinaryMerge thisbm = bmResults[i];
      if (thisbm._ansN == 0) continue;
      long thisChunkSizes[] = thisbm._chunkSizes;  // TODO: change chunkSizes to int[]
      for (int j=0; j<thisChunkSizes.length; j++) {
        chunkSizes[k] = thisChunkSizes[j];
        chunkLeftMSB[k] = thisbm._leftMSB;
        chunkRightMSB[k] = thisbm._rightMSB;
        chunkBatch[k] = j;
        k++;
      }
    }

    // Now we can stitch together the final frame from the raw chunks that were put into the store
    //First, create espc array
    long espc[] = new long[chunkSizes.length+1];
    i=0;
    long sum=0;
    for (long s : chunkSizes) {
      espc[i++] = sum;
      sum+=s;
    }
    espc[espc.length-1] = sum;
    assert(sum==ansN);

    // Allocate dummy vecs/chunks, to be filled in MRTask below
    int num = rightFrame.numCols();
    final byte[] types = new byte[num];
    int j=0;
    for (Vec v : rightFrame.vecs()) types[j++] = v.get_type();
    Vec[] vecs = new Vec(Vec.newKey(),espc).makeCons(num, 0, rightFrame.domains(), types);
    String[] names = rightFrame.names().clone();

    //TODO add left half
    // Now we can stitch together the final frame from the raw chunks that were put into the store
    Frame fr = new Frame(Key.make(rightFrame._key.toString() + "_joined_with_" + leftFrame._key.toString()), names, vecs);
    ChunkStitcher ff = new ChunkStitcher(leftFrame, rightFrame, chunkSizes, chunkLeftMSB, chunkRightMSB, chunkBatch);
    ff.doAll(fr);
    return fr;
  }

  static class ChunkStitcher extends MRTask<ChunkStitcher> {
    final Frame _leftFrame;
    final Frame _rightFrame;
    final long _chunkSizes[];
    final int _chunkLeftMSB[];
    final int _chunkRightMSB[];
    final int _chunkBatch[];
    public ChunkStitcher(Frame leftFrame,
                         Frame rightFrame,
                         long[] chunkSizes,
                         int[] chunkLeftMSB,
                         int[] chunkRightMSB,
                         int[] chunkBatch
    ) {
      _leftFrame = leftFrame;
      _rightFrame = rightFrame;
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
        Key k = BinaryMerge.getKeyForMSBComboPerCol(_leftFrame, _rightFrame, _chunkLeftMSB[chkIdx], _chunkRightMSB[chkIdx], i, _chunkBatch[chkIdx]);
        Chunk ck = DKV.getGet(k);
        DKV.put(destKey, ck, fs);
        DKV.remove(k);
      }
      fs.blockForPending();
    }
  }
}

