package water.rapids;

import water.fvec.*;
import water.*;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ASTGroupSorted {
   // 2^31 bytes > java max (2^31-1), so 2^30 / 8 bytes per long.   TO DO - how to make global?
  //private static final int MAXVECLONG = 134217728;
  //private static final int MAXVECBYTE = 1073741824;

  long[][] sort(Frame groupCols) {

    //return (new RadixOrder(groupCols, ArrayUtils.seq(0,groupCols.numCols()-1))._groupIndex);   // TO DO: won't work yet as needs 2nd group step
    return (new long[][] {{1,2,3}});
    // a vector

    /*
    System.out.println("Calling RadixCount ...");
    long t0 = System.nanoTime();
    long t00 = t0;
    int nChunks = groupCols.anyVec().nChunks();

    if( groupCols.numCols() != 1 )  throw H2O.unimpl(); // Only looking at column 0 for now
    long counts[][][] = new RadixCount(nChunks).doAll(groupCols.vec(0))._counts;
    System.out.println("Time of RadixCount: " + (System.nanoTime() - t0) / 1e9); t0 = System.nanoTime();
    // for (int c=0; c<5; c++) { System.out.print("First 10 for chunk "+c+" byte 0: "); for (int i=0; i<10; i++) System.out.print(counts[0][c][i] + " "); System.out.print("\n"); }

    long totalHist[] = new long[256];
    for (int c=0; c<nChunks; c++) {
      for (int h=0; h<256; h++) {
        totalHist[h] += counts[5][c][h];   // TO DO: hard coded 5 here
      }
    }

    for (int b=0; b<8; b++) {
      for (int h=0; h<256; h++) {
        long rollSum = 0;
        for (int c = 0; c < nChunks; c++) {
          long tmp = counts[b][c][h];
          counts[b][c][h] = rollSum;
          rollSum += tmp;
        }
      }
    }
    // Any radix skipping needs to be detected with a loop over node results to ensure no use of those bits on any node.
    System.out.println("Time to cumulate counts: " + (System.nanoTime() - t0) / 1e9); t0 = System.nanoTime();

    // TO DO:  by this stage we know now the width of byte field we need.  So allocate it tight up to MAXVEC
    // TO DO: reduce to 5 if we're only passed the first column
    int keySize = 7;
    long o[][][] = new long[256][][];
    byte x[][][] = new byte[256][][];  // for each bucket,  there might be > 2^31 bytes, so an extra dimension for that

    for (int c=0; c<256; c++) {
      if (totalHist[c] == 0) continue;
      int d;
      int nbatch = (int)(totalHist[c] * Math.max(keySize,8) / MAXVECBYTE);   // TO DO. can't be 2^31 because 2^31-1 was limit. If we use 2^30, instead of /, can we do >> for speed?
      int rem = (int)(totalHist[c] * Math.max(keySize,8) % MAXVECBYTE);
      assert nbatch==0;  // in the case of 20m rows, we should always be well within a batch size
      // The Math.max ensures that batches are aligned, even for wide keys.  For efficiency inside insert() above so it doesn't have to cross boundaries.
      o[c] = new long[nbatch + (rem>0?1:0)][];
      x[c] = new byte[nbatch + (rem>0?1:0)][];
      assert nbatch==0;
      for (d=0; d<nbatch; d++) {
        o[c][d] = new long[MAXVECLONG];
        // TO DO?: use MemoryManager.malloc8()
        x[c][d] = new byte[MAXVECBYTE];
      }
      if (rem>0) {
        o[c][d] = new long[rem];
        x[c][d] = new byte[rem * keySize];
      }
    }
    System.out.println("Time to allocate o[][] and x[][]: " + (System.nanoTime() - t0) / 1e9); t0 = System.nanoTime();
    // NOT TO DO: we do need the full allocation of x[] and o[].  We need o[]
    // anyway.  x[] will be as dense as possible.
    // o is the full ordering vector of the right size
    // x is the byte key aligned with o
    // o AND x are what bmerge() needs. Pushing x to each node as well as o avoids inter-node comms.

    // feasibly, that we could move by byte 5 and then skip the next byte.  Too
    // complex case though and rare so simplify
    new MoveByFirstByte(5, o, x, counts, keySize).doAll(groupCols);  
    System.out.println("Time to MoveByFirstByte: " + (System.nanoTime() - t0) / 1e9); t0 = System.nanoTime();

    // Add check that this first split is reasonable.  e.g. if it were just 2,
    // it definitely would not be enough.  90 is enough though.  Need to fill
    // L2 with pages.  
    // for counted completer 0:255
    long groups[][] = new long[256][];  //  at most MAXVEC groups per radix, currently
    long nGroup[] = new long[257];   // one extra to make undo of cumulate easier
    Futures fs = new Futures();
    for (int i=0; i<256; i++) {
      if (totalHist[i] > 0)
        fs.add(H2O.submitTask(new dradix(groups, nGroup, i, x[i], o[i], totalHist[i], keySize)));
    }
    fs.blockForPending();
    long nGroups = 0;
    for (int i = 0; i < 257; i++) {
      long tmp = nGroup[i];
      nGroup[i] = nGroups;
      nGroups += tmp;
    }
    System.out.println("Time to recursive radix: " + (System.nanoTime() - t0) / 1e9 ); t0 = System.nanoTime();
    System.out.println("Total groups found: " + nGroups);

    // We now have o and x that bmerge() needs

    long nrow = groupCols.numRows();

    long g[][] = new long[(int)(1 + nrow / MAXVECLONG)][];
    int c;
    for (c=0; c<nrow/MAXVECLONG; c++) {
      g[c] = new long[MAXVECLONG];
    }
    g[c] = new long[(int)(nrow % MAXVECLONG)];
    fs = new Futures();
    for (int i=0; i<256; i++) {
      if (totalHist[i] > 0)
        fs.add(H2O.submitTask(new assignG(g, groups[i], nGroup[i+1]-nGroup[i], nGroup[i], o[i])));
      // reuse the x vector we allocated before to store the group numbers.  i.e. a perfect and ordered hash, stored alongside table
    }
    fs.blockForPending();
    System.out.println("Time to assign group index (length nrows): " + (System.nanoTime() - t0) / 1e9 ); t0 = System.nanoTime();
    return g;
    */
  }
}
