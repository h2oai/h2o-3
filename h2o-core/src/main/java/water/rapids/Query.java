package water.rapids;

import water.Futures;
import water.H2O;
import water.MRTask;
import water.MemoryManager;
import water.fvec.C8Chunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.AtomicUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


// Now find groups ...

// Find group sizes when we know the number of groups ...
// long groups[][] = new long[256][];  //  at most MAXVEC groups per radix, currently

//// new helper class A extend MRTask.  In its reduce it'll get another A.  Make another class B extends ICED that wraps a bunch of long arrays (like I have now).
//// A contains B as a member. The long arrays are easy to serialize automatically.   As you reduce two A's, do with the two B's. 'this' in the reduce will have the target A.
//// B gets initialized in the setupLocal() of A on each node.  Check in A's reduce that the two B's have different pointers else return.   See Arno's DeepLearningTask;
//// DeepLearningModelInfo is his B.

class assignG extends H2O.H2OCountedCompleter<assignG> {
  private static final int MAXVECLONG = 134217728, MAXVECBYTE = 1073741824;
  long _gOut[][];   // wide writing to here, but thread safe due to each location written once only
  long _groups[];   // the group sizes
  long _nGroup;     // length of _groups because we over-allocated it
  long _o[][];      // the order for this segment
  long _firstGroupNum;
  assignG(long gOut[][], long groups[], long nGroup, long firstGroupNum, long o[][]) {
    _gOut = gOut;
    _groups = groups;
    _nGroup = nGroup;
    _firstGroupNum = firstGroupNum;
    _o = o;
  }
  @Override protected void compute2() {
    int oi = 0, batch=0;
    long _ob[] = _o[batch];
    long gOutBatch[] = _gOut[batch];
    for (int i=0; i<_nGroup; i++) {
      for (int j = 0; j < _groups[i]; j++) {
        long target = _ob[oi++];
        assert target < MAXVECLONG;
        gOutBatch[(int)(target)] = _firstGroupNum+i;
        if (oi == MAXVECLONG) {
          assert false;
          _ob = _o[++batch];
          oi -= MAXVECLONG;
        }
      }
    }
    tryComplete();
  }
}


  /*
    long nGroups = 0;
    for (int i = 0; i < 257; i++) {
      long tmp = nGroup[i];
      nGroup[i] = nGroups;
      nGroups += tmp;
    }
    System.out.println("Time to recursive radix: " + (System.nanoTime() - t0) / 1e9 ); t0 = System.nanoTime();
    System.out.println("Total groups found: " + nGroups);
    if (!retGrp) return;

    // We now have o and x that bmerge() needs

    long nrow = DF.numRows();

    long g[][] = new long[(int)(1 + nrow / MAXVECLONG)][];
    int c;
    for (c=0; c<nrow/MAXVECLONG; c++) {
      g[c] = new long[MAXVECLONG];
    }
    g[c] = new long[(int)(nrow % MAXVECLONG)];
    fs = new Futures();
    for (int i=0; i<256; i++) {
      if (reduceHist[i] > 0)
        fs.add(H2O.submitTask(new assignG(g, groups[i], nGroup[i+1]-nGroup[i], nGroup[i], o[i])));
      // reuse the x vector we allocated before to store the group numbers.  i.e. a perfect and ordered hash, stored alongside table
    }
    fs.blockForPending();
    System.out.println("Time to assign group index (length nrows): " + (System.nanoTime() - t0) / 1e9 ); t0 = System.nanoTime();

    //Vec res = Vec.makeZero(nGroups);  // really slow to assign to
    double res[] = new double[(int)nGroups];
    new runSum(g, res).doAll(toSum);

    // TO DO: Create final frame with 3 columns

    System.out.println("Time to sum column: " + (System.nanoTime() - t0) / 1e9 );
    System.out.println("Total time: " + (System.nanoTime() - t00) / 1e9);

    for (int i = 0; i < 5; i++) System.out.format("%7.0f\n", res[i]);
    System.out.println("---");
    for (int i = 4; i >= 0; i--) System.out.format("%7.0f\n", res[(int)nGroups-i-1]);
*/
/*
      System.out.println("Head and tail:");
      int head=10, done=0, batch = -1;
      while (done < head) {
          while (o[++batch] == null) ;
          for (int i = 0; done < head && i < o[batch][0].length; i++)
              System.out.format("%7d: %16d\n", done++, vec.at8(o[batch][0][i]));
      }

      System.out.println("--------");
      batch = 256;
      done = 0;
      while (done < head) {
          while (o[--batch] == null) ;
          for (int i = o[batch][0].length - 1; done < head && i >= 0; i--)
              System.out.format("%7d: %16d\n", vec.length() - ++done, vec.at8(o[batch][0][i]));
      }
      System.out.print("\n");
*/


/*
void cummulate(long x[][], long a, long b) {
    long rollSum = 0;
    for (int j=0; j<b; j++)
    for (int i=0; i<a; i++) {
        long tmp = x[i][j];
        x[i][j] = rollSum;
        rollSum += tmp;
    }
}
*/

class runSum extends MRTask<runSum> {
  private static final int MAXVECLONG = 134217728, MAXVECBYTE = 1073741824;
  long _g[];
  double _res[];
  runSum(long g[][], double res[]) { _g = g[0]; _res = res;}
  @Override public void map(Chunk chk) {
    // TODO: if spans 2 MAXVEC in g.  Currently hard coded to g[0]
    int j = (int)chk.start();
    for (int r=0; r<chk._len; r++) {
      AtomicUtils.DoubleArray.add(_res, (int) _g[j++], chk.at8(r));
    }
  }
}
