package ai.h2o.automl;


import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.api.schemas3.ImportFilesV3;
//import org.apache.commons.logging.Log;

import water.fvec.Frame;

public class AutoMLTest extends TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

//  @Test public void histSmallCats() {
//    Frame fr = null;
//    AutoML aml = null;
//    try {
//      fr = parse_test_file(Key.make("a.hex"), "smalldata/iris.csv");
//      aml = new AutoML(Key.<AutoML>make(),"iris_wheader", fr, 4, "", -1, -1, false, null, true);
//      aml.learn();
//    } finally {
//      if(fr!=null)  fr.delete();
//      if(aml!=null) aml.delete();
//    }
//  }
//
@Test public void checkMeta() {
  AutoML aml=null;
  try {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    autoMLBuildSpec.input_spec.training_path = new ImportFilesV3.ImportFiles();
    autoMLBuildSpec.input_spec.training_path.path = "smalldata/iris/iris_wheader.csv";
    autoMLBuildSpec.input_spec.response_column = "class";
    autoMLBuildSpec.build_control.loss = "AUTO";
    autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs(3600);

    aml = AutoML.startAutoML(autoMLBuildSpec);
    // sepal_len column
    // check the third & fourth moment computations
    Assert.assertTrue(aml.frameMetadata._cols[0]._thirdMoment == 0.17642222222222248);
    Assert.assertTrue(aml.frameMetadata._cols[0]._fourthMoment == 1.1332434671886653);
//
    // check skew and kurtosis
    Assert.assertTrue(aml.frameMetadata._cols[0]._skew == 0.31071214388181395);
    Assert.assertTrue(aml.frameMetadata._cols[0]._kurtosis == 2.410255837401182);
  } finally {
    // cleanup
    if(aml!=null) aml.getTrainingFrame().delete();
    if(aml!=null) aml.delete();
  }
}

  @Test public void SanTanderTest() {
    //Frame fr=null;
    AutoML aml=null;
    try {
      // was: makeAutoML(Key<AutoML> key, String datasetPath, String[] relationPaths, String responseName, String loss, long maxTime, double minAccuracy, boolean ensemble, algo[] excludeAlgos, boolean tryMutations )
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      autoMLBuildSpec.input_spec.training_path = new ImportFilesV3.ImportFiles();
      //autoMLBuildSpec.input_spec.training_path.path = "smalldata/santander/train.csv.zip";
      //autoMLBuildSpec.input_spec.response_column = "TARGET";
      //autoMLBuildSpec.build_control.loss = "MSE";
      //autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs(3600);

      //autoMLBuildSpec.input_spec.training_path.path = "/Users/nidhimehta/Desktop/frame_meta_testFiles/dum_2num.csv";
      //autoMLBuildSpec.input_spec.response_column = "class";
      autoMLBuildSpec.input_spec.training_path.path = "smalldata/allyears2k_headers.zip";
      autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
      autoMLBuildSpec.build_control.loss = "AUTO";
      autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs(300);

      aml = AutoML.makeAutoML(Key.<AutoML>make(), autoMLBuildSpec);
      aml.learn();

      aml = AutoML.startAutoML(autoMLBuildSpec);
      //Assert.assertTrue(aml.frameMetadata._cols[0]._skew == 0.31071214388181395);
      System.out.print(aml.frameMetadata._cols[0]._skew);

    } finally {
      // cleanup
      if(aml!=null) aml.delete();
    }
  }
}
