package water.rapids;

import water.H2ONode;
import water.RPC;
import water.fvec.Frame;

public class Merge {

  // single-threaded driver logic
  Merge(Frame leftFrame, Frame rightFrame, int leftCols[], int rightCols[]) {

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
        BinaryMerge bm = new RPC<>(rightNode,
                new BinaryMerge(leftFrame, rightFrame,
                        leftMSB, rightMSB,
                        leftNode.index(), //convention - right frame is local, but left frame is potentially remote
                        leftIndex._bytesUsed,   // field sizes for each column in the key
                        rightIndex._bytesUsed
                )
        ).call().get();

        //System.out.println(" ... first match " + bm._retFirst[0] + " len " + bm._retLen[0]);
        long lefto[][] = leftIndex._o[leftMSB];
        long righto[][] = rightIndex._o[rightMSB];
//        for (int lr = 0; lr < leftLen; lr++) {
//          System.out.print("Left row" + lefto[0][lr] + " matches to right row(s): ");
//          for (int rr = 0; rr < bm._retLen[lr]; rr++)
//            System.out.print(righto[0][(int) bm._retFirst[lr] + rr] + " ");
//          System.out.println();
//            }
//          }
//        }
      }

      //long[][] y = ((long[][])rightIndex.get(1))
      //System.out.println(i + " right " + ((byte[][][]) rightIndex.get(0))[i].length);
      //
      //System.out.println(retFirst[0]);
      // TO DO:   We don't even need to descend into all the buckets of the right, just the ones that the left matches to.
      // TO DO:   Add some small to v-large benchmarks
    }
  }
}
