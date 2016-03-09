package hex;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.InteractionWrappedVec;
import water.fvec.Vec;


// test cases:
// skipMissing = TRUE/FALSE
// useAllLevels = TRUE/FALSE
// limit enums

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
              DataInfo.InteractionPair.generatePairwiseInteractions(8, 16, 2)  // interactions
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
      Frame interactions = DataInfo.makeInteractions(fr,false,DataInfo.InteractionPair.generatePairwiseInteractions(8,16,2),true,true);
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
              DataInfo.InteractionPair.generatePairwiseInteractions(8, 16, 2)  // interactions
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
      Frame interactions = DataInfo.makeInteractions(fr,false,DataInfo.InteractionPair.generatePairwiseInteractions(8,16,2),false,true);
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
              DataInfo.InteractionPair.generatePairwiseInteractions(8, 16, 2)  // interactions
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
}
