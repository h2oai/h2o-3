package hex.hglm;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class HGLMBasicTestHGLM extends TestUtil {
  
  @Test
  public void testHGLMModelProstate() {
    Scope.enter();
    try {
      Frame prostate = parseAndTrackTestFile("smalldata/prostate/prostate.csv");
      prostate.replace(3, prostate.vec(3).toCategoricalVec()).remove();
      prostate.replace(4, prostate.vec(4).toCategoricalVec()).remove();
      prostate.replace(5, prostate.vec(5).toCategoricalVec()).remove();
      DKV.put(prostate);
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = prostate._key;
      params._response_column = "VOL";
      params._ignored_columns = new String[]{"ID"};
      params._group_column = "DPROS";
      params._use_all_factor_levels = true;
      params._random_columns = new String[]{"RACE", "DCAPS", "GLEASON"};
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testSemiconductor() {
/*    try {
      Scope.enter();
      Frame fr = parseTestFile("smalldata/glm_test/semiconductor.csv");
      fr.replace(0, fr.vec(0).toCategoricalVec()).remove();
      DKV.put(fr);
      Scope.track(fr);
      GLMParameters parms = new GLMParameters();
      parms._train = fr._key;
      parms._response_column = "y";)
      parms._ignored_columns = new String[]{"x2", "x4", "Device"};
      parms._ignore_const_cols = true;
      parms._family = Family.gaussian;
      parms._link = GLMParameters.Link.identity;
      parms._HGLM = true;
      parms._rand_family = new Family[]{Family.gaussian};
      parms._rand_link = new GLMParameters.Link[]{GLMParameters.Link.identity};
      parms._random_columns = new int[]{0};
      parms._calc_like = true;

      // just make sure it runs
      GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);
      ModelMetricsHGLMGaussianGaussian mmetrics = (ModelMetricsHGLMGaussianGaussian) model._output._training_metrics;
      Scope.track_generic(mmetrics);
      assertEquals(363.6833, mmetrics._hlik, 1e-4);
      System.out.println("**************** testSemiconductor test completed. ****************");
    } finally {
      Scope.exit();
    }*/
  }

  @Test
  public void testMultiChunkData(){
/*    try {
      Scope.enter();
      Frame fr = parseTestFile("smalldata/glm_test/HGLM_5KRows_100Z.csv");
      fr.replace(0, fr.vec(0).toCategoricalVec()).remove();
      fr.replace(1, fr.vec(1).toCategoricalVec()).remove();
      fr.replace(2, fr.vec(2).toCategoricalVec()).remove();
      fr.replace(3, fr.vec(3).toCategoricalVec()).remove();
      DKV.put(fr);
      Scope.track(fr);
      GLMParameters parms = new GLMParameters();
      parms._train = fr._key;
      parms._response_column = "response";
      parms._ignored_columns = new String[]{"Z"};
      parms._ignore_const_cols = true;
      parms._family = Family.gaussian;
      parms._link = GLMParameters.Link.identity;
      parms._HGLM=true;
      parms._rand_family = new Family[] {Family.gaussian};
      parms._rand_link = new GLMParameters.Link[] {GLMParameters.Link.identity};
      parms._random_columns = new int[]{0};
      parms._calc_like = true;

      // just make sure it runs
      GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);
      ModelMetricsHGLMGaussianGaussian mmetrics = (ModelMetricsHGLMGaussianGaussian) model._output._training_metrics;
      Scope.track_generic(mmetrics);
      assertEquals(-23643.3076231, mmetrics._hlik, 1e-4);
    } finally {
      Scope.exit();
    }*/
  }
}
