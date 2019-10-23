package ai.h2o.targetencoding.strategy;

import ai.h2o.targetencoding.TargetEncoderModel;
import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceWalker;
import hex.schemas.TargetEncoderV3;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.api.GridSearchHandler;

import java.util.HashMap;

public class TargetEncoderRGSTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void getTargetEncodingMapByTrainingTEBuilder() {

    Scope.enter();
    try {
      HashMap<String, Object[]> hpGrid = new HashMap<>();
      hpGrid.put("blending", new Boolean[]{true, false});
      hpGrid.put("noise_level", new Double[]{0.0, 0.01,  0.1});
      // TODO figure out how to work with hierarchical parameters BlendingParams(inflection_point, smoothing) or BlendingParams(k, f)
//      hpGrid.put("_inflection_point", new Double[]{1.0, 2.0, 3.0, 5.0, 10.0, 50.0, 100.0});
//      hpGrid.put("_smoothing", new Double[]{5.0, 10.0, 20.0});
      
      TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

      GridSearchHandler.DefaultModelParametersBuilderFactory<TargetEncoderModel.TargetEncoderParameters, TargetEncoderV3.TargetEncoderParametersV3> modelParametersBuilderFactory = new GridSearchHandler.DefaultModelParametersBuilderFactory<>();

      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker = new HyperSpaceWalker.RandomDiscreteValueWalker<>(parameters, hpGrid, modelParametersBuilderFactory, hyperSpaceSearchCriteria);

      HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = walker.iterator();
      
      while (iterator.hasNext(null)) {
        TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = iterator.nextModelParameters(null);
        System.out.println( targetEncoderParameters._blending + ":" +  targetEncoderParameters._noise_level);
      }
    } finally {
      Scope.exit();
    }
  }

}
