package water.rapids;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.util.FileUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import static junit.framework.TestCase.assertTrue;


public class MungingTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }



  private void copyStream(OutputStream os, InputStream is, final int buffer_size) {
    try {
      byte[] bytes=new byte[buffer_size];
      for(;;) {
        int count=is.read(bytes, 0, buffer_size);
        if( count<=0 ) break;
        os.write(bytes, 0, count);
      }
    }
    catch(Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Test
  public void testRankWithinGroupby() {
    try {
      Scope.enter();
      // generate training frame randomly
      Random generator = new Random();
      int numRowsG = generator.nextInt(10000) + 15000 + 200;
      int groupby_factors = generator.nextInt(5) + 2;
      Frame groupbyCols = TestUtil.generate_enum_only(2, numRowsG, groupby_factors, 0);
      Scope.track(groupbyCols);
      Frame sortCols = TestUtil.generate_int_only(2, numRowsG, groupby_factors*2, 0.01);
      Scope.track(sortCols);
      Frame train = groupbyCols.add(sortCols);  // complete frame generation
      Scope.track(train);

      String newCol = "new_rank_col";
      Frame tempFrame = generateResult(train, new int[] {0, 1}, new int[]{2, 3}, newCol);
      Frame answerFrame = tempFrame.sort(new int[]{0,1,2,3}, new int[]{1,1,1,1});
      Scope.track(tempFrame);
      Scope.track(answerFrame);
      String x = String.format("(rank_within_groupby %s [0,1] [2,3] [1,1] %s 0)",train._key, newCol);
      Val res = Rapids.exec(x);
      Frame finalResult  = res.getFrame();  // need to compare this to correct result
      Scope.track(finalResult);
      assertTrue(isIdenticalUpToRelTolerance(finalResult, answerFrame, 1e-10));
    } finally {
      Scope.exit();
    }
  }

  public static int findKeyIndex(ArrayList<double[]> tempMap, double[] currKey) {
    if (tempMap == null || tempMap.size()==0)
      return -1;

    int arraySize = tempMap.size();
    for (int aIndex = 0; aIndex < arraySize; aIndex++)
      if (Arrays.equals(tempMap.get(aIndex), currKey))
        return aIndex;
    return -1;
  }

  public Frame generateResult(Frame inputFrame, int[] groupbyCols, int[] sortCols, String newRankCol) {
    Frame sortedFrame = inputFrame.sort(sortCols, new int[]{1, 1}); // sorted frame here.
    Vec rankVec = inputFrame.anyVec().makeCon(Double.NaN);
    sortedFrame.add(newRankCol, rankVec);  // add new rank column of invalid rank, NAs

    int groupbyLen = groupbyCols.length;
    double[] key = new double[groupbyLen];
    int currentRank = 1;
    int rankCol = sortedFrame.numCols() - 1;
    ArrayList<double[]> keys = new ArrayList<>();
    ArrayList<Integer> accuRanks = new ArrayList<>();

    for (long rowIndex = 0; rowIndex < sortedFrame.numRows(); rowIndex++) {
      boolean nasFound = false;
      for (int sInd : sortCols) {
        if (Double.isNaN(sortedFrame.vec(sInd).at(rowIndex))) {
          nasFound = true;
          continue;
        }
      }
      // always read in the group keys regardless of NAs
      for (int cind = 0; cind < groupbyLen; cind++) {
        key[cind] = sortedFrame.vec(groupbyCols[cind]).at(rowIndex);
      }
      if (!nasFound) {
        int index = findKeyIndex(keys, key);
        if (index < 0) {  // new key
          keys.add(Arrays.copyOf(key, groupbyLen));
          accuRanks.add(2); //
          currentRank = 1;
        } else {  // existing key
          currentRank = accuRanks.get(index);
          accuRanks.set(index, currentRank+1);

        }
        sortedFrame.vec(rankCol).set(rowIndex, currentRank);
      }
    }
    return sortedFrame;
  }

  @Ignore @Test public void run2() throws Exception {
    System.out.println("Running run2 ...");
    NFSFileVec nfs = TestUtil.makeNfsFileVec("/home/mdowle/devtestdata/step1.csv");
    Frame frame = ParseDataset.parse(Key.make(), nfs._key);  // look into parse() to manip column types
    System.out.println("Loaded file, now calling Query ...");
    // new RadixOrder(frame, true, new int[] {0,1});   // group by 0=id, 1=date   and sum 3 == quantity
    // TO DO: change back to DoGroup(frame, new int[] {0,1}, frame.vec(3), true)
    frame.delete();
  }

  @Ignore @Test public void run3() throws Exception {
    System.out.println("Running run3 ...");

    NFSFileVec nfs = TestUtil.makeNfsFileVec("/home/mdowle/devtestdata/step1_subset.csv");
    //NFSFileVec nfs = NFSFileVec.make(find_test_file("/users/arno/devtestdata/step1_subset.csv"));
    Frame leftFrame = ParseDataset.parse(Key.make(), nfs._key);

    //nfs = NFSFileVec.make(find_test_file("/home/mdowle/devtestdata/fullsize.csv"));
    nfs = NFSFileVec.make(FileUtils.locateFile("/home/mdowle/devtestdata/fullsize.csv"));
    //nfs = NFSFileVec.make(find_test_file("/users/arno/devtestdata/fullsize.csv"));
    Frame rightFrame = ParseDataset.parse(Key.make(), nfs._key);  // look into parse() to manip column types

    System.out.println("Loaded two files, now calling order ...");

    // TO DO: this would be nice to see in chunk summary ...
    // for (int i=0; i<rightFrame.anyVec().nChunks(); i++) {
    //   Log.info("Chunk " + i + " is on node " + rightFrame.anyVec().chunkKey(i).home_node().index());
    // }

    // Frame fr1 = Merge.merge(leftFrame, rightFrame, new int[] {0,1}, new int[] {0,1}, false);  // 0==id, 1==date  (no dups)
    // Frame fr2 = Merge.merge(leftFrame, rightFrame, new int[] {0},   new int[] {0}, false  );  // 0==id           (many dups)


    //Log.info(fr1.toString(0,(int)fr1.numRows()));
    //Log.info(fr2.toString(0,(int)fr2.numRows()));

//      NFSFileVec ref1 = NFSFileVec.make(find_test_file("/users/arno/devtestdata/ref1.csv"));
//      Frame ref1Frame = ParseDataset.parse(Key.make(), nfs._key);
//      Assert.assertTrue("First Join is not correct", TestUtil.isBitIdentical(fr1, ref1Frame));
//
//      NFSFileVec ref2 = NFSFileVec.make(find_test_file("/users/arno/devtestdata/ref2.csv"));
//      Frame ref2Frame = ParseDataset.parse(Key.make(), nfs._key);
//      Assert.assertTrue("First Join is not correct", TestUtil.isBitIdentical(fr2, ref2Frame));
//
//      ref1Frame.delete();
//      ref2Frame.delete();
    //fr1.delete();
    //fr2.delete();
    leftFrame.delete();
    rightFrame.delete();
    //Merge.cleanUp();
  }


//    @Test public void run1() throws Exception {
//        System.out.println("Running run1 ...");
//        NFSFileVec nfs = NFSFileVec.make(find_test_file("sapplytest.csv"));
//        Frame frame = ParseDataset.parse(Key.make(), nfs._key);  // look into parse() to manip column types
//
//        long t0 = System.nanoTime();
//        System.out.println("File loaded, now grouping using ASTGroupBy ...");
//        int _colIdx = frame.find("QTY");
//        AGG[] agg = new AGG[]{new AGG("sum", _colIdx, "rm", "QTY", null, null)};
//        long _by[] = new long[]{ frame.find("ID"), frame.find("DATE") };
//        GBTask p1 = new GBTask(_by, agg).doAll(frame);
///*
//        do = new do;
//        do.add("sum", ...);  // as string "sum"     *fun(fdjfj, fdfhdh)
//        do.add("mean", ...); // one array of strings, or a set of arguments type string or integer, array of objects in mixed types
//                             // how to do  (colA+colB)/2
//                             // Groovy and BeanScript
//                             // UDFs in Java
//                             // Prithvi:  infix rapids as string then parse
//        frame.groupBy(by =, do = );
//        frame.query("sum(QTY)", by="");
//
//        frame.query("SELECT sum(QTY), UDF(anothercol) GROUP BY ID, DATE");
//        ... Java has, but same type
//
//        // DT[, .(QTY = sum(QTY)), keyby=.(ID,DATE)]
//        new DT("(sum (col frame QTY))",new String[]{"ID", "DATE"}).doAll(frame);
//
//        int nGrps = p1._g.size();
//        G[] tmpGrps = p1._g.keySet().toArray(new G[nGrps]);
//        while( tmpGrps[nGrps-1]==null ) nGrps--;
//        final G[] grps = new G[nGrps];
//        System.arraycopy(tmpGrps,0,grps,0,nGrps);
//        H2O.submitTask(new ParallelPostGlobal(grps, nGrps, new long[]{0,1})).join();
//        Arrays.sort(grps);
//
//        // build the output
//        final int nCols = _by.length+agg.length;
//
//        // dummy vec
//        Vec v = Vec.makeZero(nGrps);
//
//        // the names of columns
//        String[] names = new String[nCols];
//        String[][] domains = new String[nCols][];
//        for( int i=0;i<_by.length;++i) {
//            names[i] = frame.name((int) _by[i]);
//            domains[i] = frame.domains()[(int)_by[i]];
//        }
//        System.arraycopy(AGG.names(agg),0,names,_by.length,agg.length);
//
//        final AGG[] _agg=agg;
//        Frame f=new MRTask() {
//            @Override public void map(Chunk[] c, NewChunk[] ncs) {
//                int start=(int)c[0].start();
//                for( int i=0;i<c[0]._len;++i) {
//                    G g = grps[i+start];
//                    int j=0;
//                    for(;j<g._ds.length;++j)
//                        ncs[j].addNum(g._ds[j]);
//
//                    for(int a=0; a<_agg.length;++a) {
//                        byte type = _agg[a]._type;
//                        switch( type ) {
//                            case AGG.T_N:  ncs[j++].addNum(g._N       );  break;
//                            case AGG.T_AVG:ncs[j++].addNum(g._avs[a]  );  break;
//                            case AGG.T_MIN:ncs[j++].addNum(g._min[a]  );  break;
//                            case AGG.T_MAX:ncs[j++].addNum(g._max[a]  );  break;
//                            case AGG.T_VAR:ncs[j++].addNum(g._vars[a] );  break;
//                            case AGG.T_SD :ncs[j++].addNum(g._sdevs[a]);  break;
//                            case AGG.T_SUM:ncs[j++].addNum(g._sum[a]  );  break;
//                            case AGG.T_SS :ncs[j++].addNum(g._ss [a]  );  break;
//                            case AGG.T_ND: ncs[j++].addNum(g._ND[a]   );  break;
//                            case AGG.T_F:  ncs[j++].addNum(g._f[a]    );  break;
//                            case AGG.T_L:  ncs[j++].addNum(g._l[a]    );  break;
//                            default:
//                                throw new IllegalArgumentException("Unsupported aggregation type: " + type);
//                        }
//                    }
//                }
//            }
//        }.doAll(nCols,v).outputFrame(names,domains);
//        p1._g=null;   // this frees up all mem in hash map
//
//        System.out.print(f.toString(0,10));
//        System.out.println("Time of aggregation (sec): " + (System.nanoTime() - t0) / 1e9);
//
//        InputStream is = (f).toCSV(true,false);
//
//        PersistManager pm = H2O.getPM();
//        OutputStream os = null;
//        try {
//            os = pm.create("/Users/arno/devtestdata/h2oOut.csv", true);
//            copyStream(os, is, 4 * 1024 * 1024);
//        } finally {
//            if (os != null) {
//                try {
//                    os.close();
//                }
//                catch (Exception e) {
//                    Log.err(e);
//                }
//            }
//        }
//
//
//        frame.delete();
//        f.delete();
//        v.remove();
//
//    }

}
