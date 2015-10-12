package water.rapids;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.*;
import water.parser.ParseDataset;
//import water.persist.PersistManager;
//import water.util.Log;
//import water.rapids.ASTGroupBy.*;

import javax.management.Query;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
//import java.util.Arrays;


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
    @Test public void run2() throws Exception {
        System.out.println("Running run2 ...");
        NFSFileVec nfs = NFSFileVec.make(find_test_file("/home/mdowle/devtestdata/step1.csv"));
        Frame frame = ParseDataset.parse(Key.make(), nfs._key);  // look into parse() to manip column types
        System.out.println("Loaded file, now calling Query ...");
        new RadixOrder(frame, new int[] {0,1});   // group by 0=id, 1=date   and sum 3 == quantity
        // TO DO: change back to DoGroup(frame, new int[] {0,1}, frame.vec(3), true)
        frame.delete();
    }

    @Test public void run3() throws Exception {
      System.out.println("Running run3 ...");

      NFSFileVec nfs = NFSFileVec.make(find_test_file("/home/mdowle/devtestdata/step1_subset.csv"));
      Frame leftFrame = ParseDataset.parse(Key.make(), nfs._key);

      nfs = NFSFileVec.make(find_test_file("/home/mdowle/devtestdata/test.csv"));
      Frame rightFrame = ParseDataset.parse(Key.make(), nfs._key);  // look into parse() to manip column types

      System.out.println("Loaded two files, now calling order ...");

      new Merge(leftFrame, rightFrame, new int[] {0,1}, new int[] {0,1});  // 0==id, 1==date  (no dups)
      new Merge(leftFrame, rightFrame, new int[] {0}, new int[] {0});      // 0==id           (many dups)

      leftFrame.delete();
      rightFrame.delete();
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
//            os = pm.create("/home/mdowle/devtestdata/h2oOut.csv", true);
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
