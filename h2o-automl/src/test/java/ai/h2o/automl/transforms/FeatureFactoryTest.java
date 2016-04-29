package ai.h2o.automl.transforms;

import ai.h2o.automl.TestUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;

public class FeatureFactoryTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }
//  @Test public void testAirlines() {
//    Frame fr=null;
//    FeatureFactory ff=null;
//    try {
//      fr = parse_test_file("smalldata/allyears2k_headers.zip");
//      String response="IsDepDelayed";
//      String[] preds = new String[]{ "Year", "Month", "DayofMonth", "DayOfWeek",
//              "CRSDepTime", "CRSArrTime", "UniqueCarrier",
//              "CRSElapsedTime", "Origin", "Dest", "Distance"};
//
//      ff = new FeatureFactory(fr,preds,response,true);
//      ff.synthesizeBasicFeatures();
//      ff.synthesizeAggFeatures();
//      System.out.println("Done!");
//
//    } finally {
//      if( null!=fr ) fr.delete();
//      if( null!=ff ) ff.delete();
//    }
//  }
//  @Test public void testMI() {
//    Frame fr=null;
//    MISelector miSelector=null;
//    try {
//      fr = parse_test_file("smalldata/allyears2k_headers.zip");
//      String response="IsDepDelayed";
//      String[] preds = new String[]{
//              "Year", "Month", "DayofMonth", "DayOfWeek",
//              "CRSDepTime", "CRSArrTime", "UniqueCarrier",
//              "CRSElapsedTime", "Origin", "Dest", "Distance"};
//
//      miSelector = new MISelector(fr,preds,response,true);
//      miSelector.debugRunAll();
//
//    } finally {
//      if( null!=fr ) fr.delete();
////      if( null!=miSelector ) miSelector.delete();
//    }
//  }

  @Test public void test2() {
    Frame fr=null;
    Frame labels=null;
    MISelector miSelector;
    try {
      fr = parse_test_file("/Users/spencer/Downloads/dummydata_h2o.csv");
      labels = parse_test_file("/Users/spencer/Downloads/dummydata_labels.csv");

      miSelector = new MISelector(fr,null,"",true);
      miSelector.debugRunAll();

    } finally {
      if( null!=fr ) fr.delete();
//      if( null!=miSelector ) miSelector.delete();
    }
  }
}
