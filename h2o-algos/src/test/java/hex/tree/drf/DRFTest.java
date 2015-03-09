package hex.tree.drf;


import org.junit.*;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

public class DRFTest extends TestUtil {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  abstract static class PrepData { abstract int prep(Frame fr); }

  static final String[] s(String...arr)  { return arr; }
  static final long[]   a(long ...arr)   { return arr; }
  static final long[][] a(long[] ...arr) { return arr; }

  @Test public void testClassIris1() throws Throwable {

    // iris ntree=1
    // the DRF should  use only subset of rows since it is using oob validation
    basicDRFTestOOBE(
          "./smalldata/iris/iris.csv","iris.hex",
          new PrepData() { @Override int prep(Frame fr) { return fr.numCols()-1; } },
          1,
          a( a(12, 0,  0),
             a(0, 14,  1),
             a(0, 1, 15)),
          s("Iris-setosa","Iris-versicolor","Iris-virginica") );

  }

  @Test public void testClassIris5() throws Throwable {
    // iris ntree=50
    basicDRFTestOOBE(
          "./smalldata/iris/iris.csv","iris.hex",
          new PrepData() { @Override int prep(Frame fr) { return fr.numCols()-1; } },
          5,
          a( a(27, 0,  0),
             a(0, 25,  2),
             a(0,  5, 24)),
          s("Iris-setosa","Iris-versicolor","Iris-virginica") );
  }

  @Test public void testClassCars1() throws Throwable {
    // cars ntree=1
    basicDRFTestOOBE(
        "./smalldata/junit/cars.csv","cars.hex",
        new PrepData() { @Override int prep(Frame fr) { DKV.remove(fr.remove("name")._key); return fr.find("cylinders"); } },
        1,
        a( a(0,  0, 0, 0, 0),
           a(0, 62, 0, 7, 0),
           a(0,  1, 0, 0, 0),
           a(0,  0, 0,31, 0),
           a(0,  0, 0, 0,40)),
        s("3", "4", "5", "6", "8"));
  }

  @Test public void testClassCars5() throws Throwable {
    basicDRFTestOOBE(
        "./smalldata/junit/cars.csv","cars.hex",
        new PrepData() { @Override int prep(Frame fr) { DKV.remove(fr.remove("name")._key); return fr.find("cylinders"); } },
        5,
        a( a(3,   0, 0,  0,   0),
           a(0, 173, 2,  9,   0),
           a(0,   1, 1,  0,   0),
           a(0,   2, 2, 68,   2),
           a(0,   0, 0,  2,  88)),
        s("3", "4", "5", "6", "8"));
  }

  @Ignore @Test public void testConstantCols() throws Throwable {
    try {
      basicDRFTestOOBE(
        "./smalldata/poker/poker100","poker.hex",
        new PrepData() { @Override int prep(Frame fr) {
          for (int i=0; i<7;i++) DKV.remove(fr.remove(3)._key);
          return 3;
        } },
        1,
        null,
        null);
      Assert.fail();
    } catch( IllegalArgumentException iae ) { /*pass*/ }
  }

  @Ignore @Test public void testBadData() throws Throwable {
    basicDRFTestOOBE(
        "./smalldata/test/drf_infinitys.csv","infinitys.hex",
        new PrepData() { @Override int prep(Frame fr) { return fr.find("DateofBirth"); } },
        1,
        a( a(6, 0),
           a(9, 1)),
        s("0", "1"));
  }

  //@Test
  public void testCreditSample1() throws Throwable {
    basicDRFTestOOBE(
        "./smalldata/kaggle/creditsample-training.csv.gz","credit.hex",
        new PrepData() { @Override int prep(Frame fr) {
          DKV.remove(fr.remove("MonthlyIncome")._key); return fr.find("SeriousDlqin2yrs");
          } },
        1,
        a( a(46294, 202),
           a( 3187, 107)),
        s("0", "1"));

  }

  //@Ignore("We need to have proper regression test.")
  //@Test
  public void testCreditProstate1() throws Throwable {
    basicDRFTestOOBE(
        "./smalldata/logreg/prostate.csv","prostate.hex",
        new PrepData() { @Override int prep(Frame fr) {
          DKV.remove(fr.remove("ID")._key); return fr.find("CAPSULE");
          } },
        1,
        a( a(46294, 202),
           a( 3187, 107)),
        s("0", "1"));

  }


  /*@Test*/ public void testAirlines() throws Throwable {
    basicDRFTestOOBE(
        "./smalldata/airlines/allyears2k_headers.zip","airlines.hex",
        new PrepData() {
          @Override int prep(Frame fr) {
            DKV.remove(fr.remove("DepTime")._key);
            DKV.remove(fr.remove("ArrTime")._key);
            DKV.remove(fr.remove("ActualElapsedTime")._key);
            DKV.remove(fr.remove("AirTime")._key);
            DKV.remove(fr.remove("ArrDelay")._key);
            DKV.remove(fr.remove("DepDelay")._key);
            DKV.remove(fr.remove("Cancelled")._key);
            DKV.remove(fr.remove("CancellationCode")._key);
            DKV.remove(fr.remove("CarrierDelay")._key);
            DKV.remove(fr.remove("WeatherDelay")._key);
            DKV.remove(fr.remove("NASDelay")._key);
            DKV.remove(fr.remove("SecurityDelay")._key);
            DKV.remove(fr.remove("LateAircraftDelay")._key);
            DKV.remove(fr.remove("IsArrDelayed")._key);
            return fr.find("IsDepDelayed"); }
        },
        50,
        a( a(14890, 5997),
           a( 6705,16386)),
        s("NO", "YES"));
  }



  // Put response as the last vector in the frame and return it.
  // Also fill DRF.
  static Vec unifyFrame(DRFModel.DRFParameters drf, Frame fr, PrepData prep) {
    int idx = prep.prep(fr);
    if( idx < 0 ) { drf._convert_to_enum = false; idx = ~idx; }
    String rname = fr._names[idx];
    drf._response_column = fr.names()[idx];
    Vec resp = fr.remove(idx);           // Move response to the end
    fr.add(rname,resp);
    return fr.lastVec();
  }

  public void basicDRFTestOOBE(String fnametrain, String hexnametrain, PrepData prep, int ntree, long[][] expCM, String[] expRespDom) throws Throwable { basicDRF(fnametrain, hexnametrain, null, null, prep, ntree, expCM, expRespDom, 10/*max_depth*/, 20/*nbins*/, 0/*optflag*/); }
  public void basicDRF(String fnametrain, String hexnametrain, String fnametest, String hexnametest, PrepData prep, int ntree, long[][] expCM, String[] expRespDom, int max_depth, int nbins, int optflags) throws Throwable {
    DRFModel.DRFParameters drf = new DRFModel.DRFParameters();
    Key destTrain = Key.make(hexnametrain);
    Key destTest  = hexnametest!=null?Key.make(hexnametest):null;
    Frame frTest = null, pred = null;
    Frame frTrain = null;
    DRFModel model = null;
    try {
      frTrain = parse_test_file(fnametrain);
      unifyFrame(drf, frTrain, prep);
      // Configure DRF
      drf._train = frTrain._key;
      drf._response_column = ((Frame)DKV.getGet(drf._train)).lastVecName();
      drf._convert_to_enum = true;
      drf._ntrees = ntree;
      drf._max_depth = max_depth;
      drf._min_rows = 1; // = nodesize
      drf._nbins = nbins;
      drf._mtries = -1;
      drf._sample_rate = 0.66667f;   // Simulated sampling with replacement
      drf._seed = (1L<<32)|2;
      drf._destination_key = Key.make("DRF_model_4_" + hexnametrain);

      // Invoke DRF and block till the end
      DRF job = null;
      try {
        job = new DRF(drf);
        // Get the model
        model = job.trainModel().get();
      } finally {
        if (job != null) job.remove();
      }

      // And compare CMs
//      assertCM(expCM, model.cms[model.cms.length-1]._arr);
//      Assert.assertEquals("Number of trees differs!", ntree, model.errs.length-1);
//      String[] cmDom = model._domains[model._domains.length-1];
//      Assert.assertArrayEquals("CM domain differs!", expRespDom, cmDom);

      frTest = fnametest!=null ? parse_test_file(fnametest) : null;
      pred = model.score(frTest);

    } finally {
      if (frTrain!=null) frTrain.remove();
      if (frTest!=null) frTest.remove();
      if( model != null ) model.delete(); // Remove the model
      if( pred != null ) pred.delete();
    }
  }
}
