package hex;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;


// test cases:
// skipMissing = TRUE/FALSE
// useAllLevels = TRUE/FALSE
// limit enums
// (dont) standardize predictor columns

// data info tests with interactions
public class DataInfoTest extends TestUtil {

  @BeforeClass static public void setup() {  stall_till_cloudsize(1); }


  @Test public void testAirlines1() { // just test that it works at all
    Frame fr = parseTestFile(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
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
              Model.InteractionSpec.allPairwise(new String[]{fr.name(8),fr.name(16),fr.name(2)})  // interactions
      );
      dinfo.dropInteractions();
      dinfo.remove();
    } finally {
      fr.delete();
    }
  }


  @Test public void testAirlines2() {
    Frame fr = parseTestFile(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
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
              Model.InteractionSpec.allPairwise(new String[]{fr.name(8),fr.name(16),fr.name(2)})   // interactions
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
    Frame fr = parseTestFile(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
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
              Model.InteractionSpec.allPairwise(new String[]{fr.name(8),fr.name(16),fr.name(2)})  // interactions
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

  @Test public void testAirlinesInteractionSpec() {
    try {
      Scope.enter();
      Frame fr = Scope.track(parseTestFile(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip"));
      Model.InteractionSpec interactionSpec = Model.InteractionSpec.create(
              null,
              new StringPair[]{new StringPair("UniqueCarrier", "Origin"), new StringPair("Origin", "DayofMonth")},
              new String[]{"UniqueCarrier"}
      );
      DataInfo dinfo = new DataInfo(
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
              interactionSpec  // interactions
      );
      Scope.track_generic(dinfo);

      Assert.assertArrayEquals(new String[]{
              "TailNum", "UniqueCarrier_Origin", "Dest", "Origin", "CancellationCode", "IsArrDelayed", "Origin_DayofMonth",
              "Year", "Month", "DayofMonth", "DayOfWeek", "DepTime", "CRSDepTime", "ArrTime", "CRSArrTime", "FlightNum",
              "ActualElapsedTime", "CRSElapsedTime", "AirTime", "ArrDelay", "DepDelay", "Distance", "TaxiIn", "TaxiOut",
              "Cancelled", "Diverted", "CarrierDelay", "WeatherDelay", "NASDelay", "SecurityDelay", "LateAircraftDelay",
              "IsDepDelayed"}, dinfo._adaptedFrame._names);
    } finally {
      Scope.exit();
    }
  }

  @Test public void testIris1() {  // test that getting sparseRows and denseRows produce the same results
    Frame fr = parseTestFile(Key.make("a.hex"), "smalldata/iris/iris_wheader.csv");
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
              Model.InteractionSpec.allPairwise(new String[]{fr.name(0),fr.name(1)})          // interactions
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
    Frame fr = parseTestFile(Key.make("a.hex"), "smalldata/iris/iris_wheader.csv");
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
              Model.InteractionSpec.allPairwise(new String[]{fr.name(0),fr.name(1)})          // interactions
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
    Frame fr = parseTestFile(Key.make("a.hex"), "smalldata/iris/iris_wheader.csv");
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
              Model.InteractionSpec.allPairwise(new String[]{fr.name(0),fr.name(1),fr.name(2),fr.name(3)})          // interactions
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
    Frame fr = parseTestFile(Key.make("a0.hex"), "smalldata/airlines/allyears2k_headers.zip");
    // fixme need to rebalance to 1 chunk, otherwise the test does not pass!
    Key k = Key.make("a.hex");
    H2O.submitTask(new RebalanceDataSet(fr,k,1)).join();
    fr.delete();
    fr = DKV.getGet(k);
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
              Model.InteractionSpec.allPairwise(new String[]{fr.name(8),fr.name(16),fr.name(2)})          // interactions
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
    Frame fr = parseTestFile(Key.make("a0.hex"), "smalldata/airlines/allyears2k_headers.zip");
    // fixme need to rebalance to 1 chunk, otherwise the test does not pass!
    Key k = Key.make("a.hex");
    H2O.submitTask(new RebalanceDataSet(fr,k,1)).join();
    fr.delete();
    fr = DKV.getGet(k);
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
              Model.InteractionSpec.allPairwise(new String[]{fr.name(8),fr.name(16),fr.name(2)})           // interactions
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

  @Test public void testCoefNames() throws IOException { // just test that it works at all
    Frame fr = parseTestFile(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
    DataInfo dinfo = null;
    try {
      dinfo = new DataInfo(
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
              Model.InteractionSpec.allPairwise(new String[]{fr.name(8),fr.name(16),fr.name(2)})  // interactions
      );

      Assert.assertNull(dinfo._coefNames); // coef names are not populated at first
      final String[] cn = dinfo.coefNames();
      Assert.assertNotNull(cn);
      Assert.assertArrayEquals(cn, dinfo._coefNames); // coef names are cached after first accessed

      DKV.put(dinfo);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      dinfo.writeAll(new AutoBuffer(baos, true)).close();
      baos.close();

      ByteArrayInputStream input = new ByteArrayInputStream(baos.toByteArray());
      DataInfo deserialized = (DataInfo) Keyed.readAll(new AutoBuffer(input));
      Assert.assertNotNull(deserialized);
      Assert.assertArrayEquals(cn, deserialized._coefNames); // coef names were preserved in the deserialized object
    } finally {
      if (dinfo != null) {
        dinfo.dropInteractions();
        dinfo.remove();
      }
      fr.delete();
    }
  }

  @Test public void testInteractionsForcedAllFactors() {
    try {
      Scope.enter();
      Frame fr = Scope.track(parseTestFile(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip"));
      Frame sfr = fr.subframe(new String[]{"Origin", "Distance"});
      Model.InteractionSpec interactionSpec = Model.InteractionSpec.create(
              new String[]{"Origin", "Distance"}, null, new String[] {"Distance"});
      DataInfo dinfo = new DataInfo(
              sfr,  // train
              null,        // valid
              1,           // num responses
              false,        // DON'T use all factor levels
              DataInfo.TransformType.STANDARDIZE,  // predictor transform
              DataInfo.TransformType.NONE,         // response  transform
              true,        // skip missing
              false,       // impute missing
              false,       // missing bucket
              false,       // weight
              false,       // offset
              false,       // fold
              interactionSpec  // interaction spec
      );
      assertEquals(fr.vec("Origin").domain().length, dinfo.coefNames().length);
      String[] expected = new String[dinfo.coefNames().length];
      for (int i = 0; i < expected.length; i++)
        expected[i] = "Origin_Distance." + sfr.vec("Origin").domain()[i];
      Assert.assertArrayEquals(expected, dinfo.coefNames());
      dinfo.dropInteractions();
      dinfo.remove();
    } finally {
      Scope.exit();
    }
  }

  @Test public void testInteractionsSkip1stFactor() {
    try {
      Scope.enter();
      Frame fr = Scope.track(parseTestFile(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip"));
      Frame sfr = fr.subframe(new String[]{"Origin", "Distance", "IsDepDelayed"});
      Model.InteractionSpec interactionSpec = Model.InteractionSpec.create(
              new String[]{"Origin", "Distance"}, null, new String[]{"Origin"});
      DataInfo dinfo = new DataInfo(
              sfr,  // train
              null,        // valid
              1,           // num responses
              false,        // DON'T use all factor levels
              DataInfo.TransformType.STANDARDIZE,  // predictor transform
              DataInfo.TransformType.NONE,         // response  transform
              true,        // skip missing
              false,       // impute missing
              false,       // missing bucket
              false,       // weight
              false,       // offset
              false,       // fold
              interactionSpec  // interaction spec
      );
      // Check that we get correct expanded coefficients and "Distance" is not dropped
      assertEquals(fr.vec("Origin").domain().length, dinfo.coefNames().length);
      String[] expected = new String[dinfo.coefNames().length];
      expected[expected.length - 1] = "Distance";
      for (int i = 0; i < expected.length - 1; i++)
        expected[i] = "Origin_Distance." + fr.vec("Origin").domain()[i + 1];
      Assert.assertArrayEquals(expected, dinfo.coefNames());
      // Check that we can look-up "Categorical Id" for valid levels
      for (int j = /*don't use all factor levels*/ 1; j < dinfo._adaptedFrame.vec(0).domain().length; j++) {
        if (dinfo.getCategoricalIdFromInteraction(0, j) < 0)
          Assert.fail("Categorical value should be recognized: " + j);
      }
      // Check that we get "mode" for unknown level
      dinfo._valid = true;
      assertEquals(fr.vec("Origin").mode(),
              dinfo.getCategoricalIdFromInteraction(0, dinfo._adaptedFrame.vec(0).domain().length));
      dinfo.dropInteractions();
      dinfo.remove();
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testGetCategoricalIdFromInteraction() {
    try {
      Scope.enter();
      Frame fr = Scope.track(parseTestFile(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip"));
      Frame sfr = fr.subframe(new String[]{"Origin", "Distance", "IsDepDelayed"});
      Model.InteractionSpec interactionSpec = Model.InteractionSpec.create(
              new String[]{"Origin", "Distance"}, null, new String[]{"Origin"});
      DataInfo dinfo = new DataInfo(
              sfr,  // train
              null,        // valid
              1,           // num responses
              false,        // DON'T use all factor levels
              DataInfo.TransformType.STANDARDIZE,  // predictor transform
              DataInfo.TransformType.NONE,         // response  transform
              true,        // skip missing
              false,       // impute missing
              false,       // missing bucket
              false,       // weight
              false,       // offset
              false,       // fold
              interactionSpec  // interaction spec
      );
    // Check that we can look-up "Categorical Id" for valid levels
    for (int j = /*don't use all factor levels*/ 1; j < dinfo._adaptedFrame.vec(0).domain().length; j++) {
      if (dinfo.getCategoricalIdFromInteraction(0, j) < 0)
        Assert.fail("Categorical value should be recognized: " + j);
    }
    // Check that we get "mode" for unknown level
    dinfo._valid = true;
    assertEquals(fr.vec("Origin").mode(),
            dinfo.getCategoricalIdFromInteraction(0, dinfo._adaptedFrame.vec(0).domain().length));
    dinfo.dropInteractions();
    dinfo.remove();
  } finally {
    Scope.exit();
  }

}

  private static DataInfo.Row[] makeRowsOpsTestData() { // few rows to test row operations (inner product, ...)
    Frame f = TestFrameCatalog.oneChunkFewRows();
    DataInfo di = new DataInfo(f, null, 1, true, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false, false, false, null)
            .disableIntercept();

    Chunk[] chks = new Chunk[f.numCols()];
    for (int i = 0; i < chks.length; i++)
      chks[i] = di._adaptedFrame.vec(i).chunkForChunkIdx(0);

    return new DataInfo.Row[] {
            di.extractDenseRow(chks, 0, di.newDenseRow()),
            di.extractDenseRow(chks, 1, di.newDenseRow()),
            di.extractDenseRow(chks, 2, di.newDenseRow())
    };
  }

  @Test
  public void testInnerProduct() {
    Scope.enter();
    try {
      DataInfo.Row[] rs = makeRowsOpsTestData();

      assertEquals(3.44, rs[0].innerProduct(rs[0]), 0);
      assertEquals(4.08, rs[0].innerProduct(rs[1]), 0);
      assertEquals(6.72, rs[0].innerProduct(rs[2]), 0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testTwoNormSq() {
    Scope.enter();
    try {
      DataInfo.Row[] rs = makeRowsOpsTestData();

      assertEquals(3.44, rs[0].twoNormSq(), 0);
      assertEquals(rs[1].innerProduct(rs[1]), rs[1].twoNormSq(), 0);
      assertEquals(rs[2].innerProduct(rs[2]), rs[2].twoNormSq(), 0);
    } finally {
      Scope.exit();
    }
  }
  
//  @Test public void personalChecker() {
//    final Frame gold = parse_test_file(Key.make("gold"), "/Users/spencer/Desktop/ffff.csv");
//    Frame fr = parse_test_file(Key.make("a.hex"), "/Users/spencer/Desktop/iris.csv");
//    fr.swap(3,4);
//    DataInfo di0=null;
//    try {
//      di0 = new DataInfo(
//              fr.clone(),  // train
//              null,        // valid
//              1,           // num responses
//              false,       // use all factor levels
//              DataInfo.TransformType.STANDARDIZE,  // predictor transform
//              DataInfo.TransformType.NONE,  // response  transform
//              true,        // skip missing
//              false,       // impute missing
//              false,       // missing bucket
//              false,       // weight
//              false,       // offset
//              false,       // fold
//              new String[]{"Species", "Sepal.Length", "Petal.Length"}           // interactions
//      );
//      final DataInfo di=di0;
//      new MRTask() {
//        @Override public void map(Chunk[] cs) {
//          DataInfo.Row[] sparseRows = di.extractSparseRows(cs);
//          for(int i=0;i<cs[0]._len;++i) {
////            di.extractDenseRow(cs, i, r);
//            DataInfo.Row r = sparseRows[i];
//            int idx=1;
//            for (int j = di.numStart(); j < di.fullN(); ++j) {
//              double goldValue = gold.vec(idx++).at(i+cs[0].start());
//              double thisValue = r.get(j) - (di._normSub[j - di.numStart()] * di._normMul[j-di.numStart()]);
//              double diff = Math.abs(goldValue - thisValue);
//              if( diff > 1e-12 )
//                throw new RuntimeException("bonk");
//            }
//          }
//        }
//      }.doAll(di0._adaptedFrame);
//    } finally {
//      fr.delete();
//      gold.delete();
//      if( di0!=null ) {
//        di0.dropInteractions();
//        di0.remove();
//      }
//    }
//  }


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
        if(cs[0].start() == 23889){
          System.out.println("haha");
        }
        DataInfo.Row[] sparseRows = di.extractSparseRows(cs);
        DataInfo.Row r = di.newDenseRow();
        for(int i=0;i<cs[0]._len;++i) {
          di.extractDenseRow(cs, i, r);
          for (int j = 0; j < di.fullN(); ++j) {
            double sparseDoubleScaled = sparseRows[i].get(j);  // extracting sparse rows does not do the full scaling!!
            if( j>=di.numStart() ) { // finish scaling the sparse value
              sparseDoubleScaled -= (standardize?(di._normSub[j - di.numStart()] * di._normMul[j-di.numStart()]):0);
            }
            if( r.isBad() || sparseRows[i].isBad() ) {
              if( sparseRows[i].isBad() && r.isBad() ) continue;  // both bad OK
              throw new RuntimeException("dense row was "+(r.isBad()?"bad":"not bad") + "; but sparse row was "+(sparseRows[i].isBad()?"bad":"not bad"));
            }
            if( Math.abs(r.get(j)-sparseDoubleScaled) > 1e-10 ) {
              printVals(di,r,sparseRows[i]);
              throw new RuntimeException("Row mismatch on row " + i);
            }
          }
        }
      }
    }.doAll(di._adaptedFrame);
  }
}
