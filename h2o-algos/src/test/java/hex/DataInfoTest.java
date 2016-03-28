package hex;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.MRTask;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.InteractionWrappedVec;
import water.fvec.Vec;


// test cases:
// skipMissing = TRUE/FALSE
// useAllLevels = TRUE/FALSE
// limit enums
// (dont) standardize predictor columns

// data info tests with interactions
public class DataInfoTest extends TestUtil {

  @BeforeClass static public void setup() {  stall_till_cloudsize(1); }


  @Test public void testAirlines1() { // just test that it works at all
    Frame fr = parse_test_file(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
    try {
      DataInfo dinfo = new DataInfo(
              fr.clone(),  // train
              null,        // valid
              1,           // num responses
              true,        // use all factor levels
              DataInfo.TransformType.STANDARDIZE,  // predictor transform
              DataInfo.TransformType.NONE,         // response  transform
              true,        // skip missing
              false,       // impute missing
              false,       // missing bucket
              false,       // weight
              false,       // offset
              false,       // fold
              new String[]{fr.name(8),fr.name(16),fr.name(2)}  // interactions
      );
      dinfo.dropInteractions();
      dinfo.remove();
    } finally {
      fr.delete();
    }
  }


  @Test public void testAirlines2() {
    Frame fr = parse_test_file(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
    try {
      Frame interactions = Model.makeInteractions(fr, false, Model.InteractionPair.generatePairwiseInteractionsFromList(8, 16, 2), true, true,true);
      int len=0;
      for(Vec v: interactions.vecs()) len += ((InteractionWrappedVec)v).expandedLength();
      interactions.delete();
      Assert.assertTrue(len==290+132+10);

      DataInfo dinfo__noInteractions = new DataInfo(
              fr.clone(),  // train
              null,        // valid
              1,           // num responses
              true,        // use all factor levels
              DataInfo.TransformType.STANDARDIZE,  // predictor transform
              DataInfo.TransformType.NONE,         // response  transform
              true,        // skip missing
              false,       // impute missing
              false,       // missing bucket
              false,       // weight
              false,       // offset
              false,       // fold
              null
      );

      System.out.println(dinfo__noInteractions.fullN());
      System.out.println(dinfo__noInteractions.numNums());


      DataInfo dinfo__withInteractions = new DataInfo(
              fr.clone(),  // train
              null,        // valid
              1,           // num responses
              true,        // use all factor levels
              DataInfo.TransformType.STANDARDIZE,  // predictor transform
              DataInfo.TransformType.NONE,         // response  transform
              true,        // skip missing
              false,       // impute missing
              false,       // missing bucket
              false,       // weight
              false,       // offset
              false,       // fold
              new String[]{fr.name(8),fr.name(16),fr.name(2)}   // interactions
      );
      System.out.println(dinfo__withInteractions.fullN());
      Assert.assertTrue(dinfo__withInteractions.fullN() == dinfo__noInteractions.fullN() + len);
      dinfo__withInteractions.dropInteractions();
      dinfo__noInteractions.remove();
      dinfo__withInteractions.remove();
    } finally {
      fr.delete();
    }
  }

  @Test public void testAirlines3() {
    Frame fr = parse_test_file(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
    try {
      Frame interactions = Model.makeInteractions(fr, false, Model.InteractionPair.generatePairwiseInteractionsFromList(8, 16, 2), false, true, true);
      int len=0;
      for(Vec v: interactions.vecs()) len += ((InteractionWrappedVec)v).expandedLength();
      interactions.delete();
      Assert.assertTrue(len==426);

      DataInfo dinfo__noInteractions = new DataInfo(
              fr.clone(),  // train
              null,        // valid
              1,           // num responses
              false,        // use all factor levels
              DataInfo.TransformType.STANDARDIZE,  // predictor transform
              DataInfo.TransformType.NONE,         // response  transform
              true,        // skip missing
              false,       // impute missing
              false,       // missing bucket
              false,       // weight
              false,       // offset
              false,       // fold
              null
      );

      System.out.println(dinfo__noInteractions.fullN());

      DataInfo dinfo__withInteractions = new DataInfo(
              fr.clone(),  // train
              null,        // valid
              1,           // num responses
              false,        // use all factor levels
              DataInfo.TransformType.STANDARDIZE,  // predictor transform
              DataInfo.TransformType.NONE,         // response  transform
              true,        // skip missing
              false,       // impute missing
              false,       // missing bucket
              false,       // weight
              false,       // offset
              false,       // fold
              new String[]{fr.name(8),fr.name(16),fr.name(2)}  // interactions
      );
      System.out.println(dinfo__withInteractions.fullN());
      Assert.assertTrue(dinfo__withInteractions.fullN() == dinfo__noInteractions.fullN() + len);
      dinfo__withInteractions.dropInteractions();
      dinfo__noInteractions.remove();
      dinfo__withInteractions.remove();
    } finally {
      fr.delete();
    }
  }


  @Test public void testIris1() {  // test that getting sparseRows and denseRows produce the same results
    Frame fr = parse_test_file(Key.make("a.hex"), "smalldata/iris/iris_wheader.csv");
    fr.swap(1,4);
    Model.InteractionPair[] ips = Model.InteractionPair.generatePairwiseInteractionsFromList(0, 1);
    DataInfo di=null;

    try {
      di = new DataInfo(
              fr.clone(),  // train
              null,        // valid
              1,           // num responses
              true,        // use all factor levels
              DataInfo.TransformType.NONE,  // predictor transform
              DataInfo.TransformType.NONE,  // response  transform
              true,        // skip missing
              false,       // impute missing
              false,       // missing bucket
              false,       // weight
              false,       // offset
              false,       // fold
              new String[]{fr.name(0),fr.name(1)}          // interactions
      );
      checker(di,false);
    } finally {
      fr.delete();
      if( di!=null ) {
        di.dropInteractions();
        di.remove();
      }
    }
  }

  @Test public void testIris2() {  // test that getting sparseRows and denseRows produce the same results
    Frame fr = parse_test_file(Key.make("a.hex"), "smalldata/iris/iris_wheader.csv");
    fr.swap(1,4);
    Model.InteractionPair[] ips = Model.InteractionPair.generatePairwiseInteractionsFromList(0, 1);
    DataInfo di=null;
    try {
      di = new DataInfo(
              fr.clone(),  // train
              null,        // valid
              1,           // num responses
              true,        // use all factor levels
              DataInfo.TransformType.STANDARDIZE,  // predictor transform
              DataInfo.TransformType.NONE,  // response  transform
              true,        // skip missing
              false,       // impute missing
              false,       // missing bucket
              false,       // weight
              false,       // offset
              false,       // fold
              new String[]{fr.name(0),fr.name(1)}          // interactions
      );
      checker(di,true);
    } finally {
      fr.delete();
      if( di!=null ) {
        di.dropInteractions();
        di.remove();
      }
    }
  }

  @Test public void testIris3() {  // test that getting sparseRows and denseRows produce the same results
    Frame fr = parse_test_file(Key.make("a.hex"), "smalldata/iris/iris_wheader.csv");
    fr.swap(2,4);
    Model.InteractionPair[] ips = Model.InteractionPair.generatePairwiseInteractionsFromList(0, 1, 2, 3);
    DataInfo di=null;
    try {
      di = new DataInfo(
              fr.clone(),  // train
              null,        // valid
              1,           // num responses
              true,        // use all factor levels
              DataInfo.TransformType.STANDARDIZE,  // predictor transform
              DataInfo.TransformType.NONE,  // response  transform
              true,        // skip missing
              false,       // impute missing
              false,       // missing bucket
              false,       // weight
              false,       // offset
              false,       // fold
              new String[]{fr.name(0),fr.name(1),fr.name(2),fr.name(3)}          // interactions
      );
      checker(di,true);
    } finally {
      fr.delete();
      if( di!=null ) {
        di.dropInteractions();
        di.remove();
      }
    }
  }

  @Test public void testAirlines4() {
    Frame fr = parse_test_file(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
    Model.InteractionPair[] ips = Model.InteractionPair.generatePairwiseInteractionsFromList(8,16,2);
    DataInfo di=null;
    try {
      di = new DataInfo(
              fr.clone(),  // train
              null,        // valid
              1,           // num responses
              true,        // use all factor levels
              DataInfo.TransformType.STANDARDIZE,  // predictor transform
              DataInfo.TransformType.NONE,  // response  transform
              true,        // skip missing
              false,       // impute missing
              false,       // missing bucket
              false,       // weight
              false,       // offset
              false,       // fold
              new String[]{fr.name(8),fr.name(16),fr.name(2)}          // interactions
      );
      checker(di,true);
    } finally {
      fr.delete();
      if( di!=null ) {
        di.dropInteractions();
        di.remove();
      }
    }
  }

  @Test public void testAirlines5() {
    Frame fr = parse_test_file(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
    Model.InteractionPair[] ips = Model.InteractionPair.generatePairwiseInteractionsFromList(8,16,2);
    DataInfo di=null;
    try {
      di = new DataInfo(
              fr.clone(),  // train
              null,        // valid
              1,           // num responses
              false,       // use all factor levels
              DataInfo.TransformType.STANDARDIZE,  // predictor transform
              DataInfo.TransformType.NONE,  // response  transform
              true,        // skip missing
              false,       // impute missing
              false,       // missing bucket
              false,       // weight
              false,       // offset
              false,       // fold
              new String[]{fr.name(8),fr.name(16),fr.name(2)}           // interactions
      );
      checker(di,true);
    } finally {
      fr.delete();
      if( di!=null ) {
        di.dropInteractions();
        di.remove();
      }
    }
  }

  private static void printVals(DataInfo di, DataInfo.Row denseRow, DataInfo.Row sparseRow) {
    System.out.println("col|dense|sparse|sparseScaled");
    double sparseScaled;
    String line;
    for(int i=0;i<di.fullN();++i) {
      sparseScaled = sparseRow.get(i);
      if( i>=di.numStart() )
        sparseScaled -= (di._normSub[i - di.numStart()] * di._normMul[i-di.numStart()]);
      line = i+"|"+denseRow.get(i)+"|"+sparseRow.get(i)+"|"+sparseScaled;
      if( Math.abs(denseRow.get(i)-sparseScaled) > 1e-14 )
        System.out.println(">" + line + "<");
    }
  }

  private static void checker(final DataInfo di, final boolean standardize) {
    new MRTask() {
      @Override public void map(Chunk[] cs) {
        DataInfo.Row[] sparseRows = di.extractSparseRows(cs);
        for(int i=0;i<cs[0]._len;++i) {
          DataInfo.Row r = di.newDenseRow();
          di.extractDenseRow(cs, i, r);
          for (int j = 0; j < di.fullN(); ++j) {
            double sparseDoubleScaled = sparseRows[i].get(j);  // extracting sparse rows does not do the full scaling!!
            if( j>=di.numStart() ) { // finish scaling the sparse value
              sparseDoubleScaled -= (standardize?(di._normSub[j - di.numStart()] * di._normMul[j-di.numStart()]):0);
            }
            if( r.bad || sparseRows[i].bad ) {
              if( sparseRows[i].bad && r.bad ) continue;  // both bad OK
              throw new RuntimeException("dense row was "+(r.bad?"bad":"not bad") + "; but sparse row was "+(sparseRows[i].bad?"bad":"not bad"));
            }
            if( Math.abs(r.get(j)-sparseDoubleScaled) > 1e-14 ) {
              printVals(di,r,sparseRows[i]);
              throw new RuntimeException("Row mismatch on row " + i);
            }
          }
        }
      }
    }.doAll(di._adaptedFrame);
  }
}
