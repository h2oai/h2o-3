package ai.h2o.automl.tasks;


import ai.h2o.automl.TestUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;

public class DummyScoreTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void testIrisClasses() {
    Frame fr = null;
    try {
      fr = parse_test_file(Key.make("a.hex"), "/0xdata/h2o-3/smalldata/iris/iris_wheader.csv");
      double[][] dummies = DummyClassifier.getDummies(fr.vec(4), null, new String[]{"logloss", "mse"});
      System.out.println();
    } finally {
      if(fr!=null)  fr.delete();
    }
  }

  @Test public void testCovtypeClasses() {
    Frame fr = null;
    Vec delVec=null;
    try {
      fr = parse_test_file(Key.make("c.hex"), "/0xdata/h2o-3/bigdata/laptop/covtype/covtype.data");
      double[][] dummies = DummyClassifier.getDummies(delVec = fr.vec(54).toCategoricalVec(), null, new String[]{"logloss", "mse"});
      System.out.println();
    } finally {
      if(fr!=null)  fr.delete();
      if( delVec!=null ) delVec.remove();
    }
  }


  @Test public void testIrisCol1() {
    Frame fr = null;
    try {
      fr = parse_test_file(Key.make("a.hex"), "/0xdata/h2o-3/smalldata/iris/iris_wheader.csv");
      double[][] dummies = DummyRegressor.getDummies(fr.vec(0), null, new String[]{"logloss", "mse"});
      System.out.println();
    } finally {
      if(fr!=null)  fr.delete();
    }
  }
//
//  @Test public void testIrisCol2() {
//    Frame fr = null;
//    try {
//      fr = parse_test_file(Key.make("a.hex"), "/0xdata/h2o-3/smalldata/iris/iris_wheader.csv");
//      VIF[] vifs = VIF.make(fr._key, new String[]{"sepal_len", "sepal_wid","petal_len","petal_wid"}, fr.names());
//      VIF.launchVIFs(vifs);
//      int idx=0;
//      double[] vifsGolden = new double[]{
//              7.103113532646519,
//              2.0990386110226114,
//              31.397292492151717,
//              16.14156350559764};
//      for(VIF vif: vifs)
//        Assert.assertTrue(vif.vif() == vifsGolden[idx++]);
//
//    } finally {
//      if(fr!=null)  fr.delete();
//    }
//  }
//
//  @Test public void testIrisCol3() {
//    Frame fr = null;
//    try {
//      fr = parse_test_file(Key.make("a.hex"), "/0xdata/h2o-3/smalldata/iris/iris_wheader.csv");
//      VIF[] vifs = VIF.make(fr._key, new String[]{"sepal_len", "sepal_wid","petal_len","petal_wid"}, fr.names());
//      VIF.launchVIFs(vifs);
//      int idx=0;
//      double[] vifsGolden = new double[]{
//              7.103113532646519,
//              2.0990386110226114,
//              31.397292492151717,
//              16.14156350559764};
//      for(VIF vif: vifs)
//        Assert.assertTrue(vif.vif() == vifsGolden[idx++]);
//
//    } finally {
//      if(fr!=null)  fr.delete();
//    }
//  }
//
//
//  @Test public void testIrisCol4() {
//    Frame fr = null;
//    try {
//      fr = parse_test_file(Key.make("a.hex"), "/0xdata/h2o-3/smalldata/iris/iris_wheader.csv");
//      VIF[] vifs = VIF.make(fr._key, new String[]{"sepal_len", "sepal_wid","petal_len","petal_wid"}, fr.names());
//      VIF.launchVIFs(vifs);
//      int idx=0;
//      double[] vifsGolden = new double[]{
//              7.103113532646519,
//              2.0990386110226114,
//              31.397292492151717,
//              16.14156350559764};
//      for(VIF vif: vifs)
//        Assert.assertTrue(vif.vif() == vifsGolden[idx++]);
//
//    } finally {
//      if(fr!=null)  fr.delete();
//    }
//  }


}
