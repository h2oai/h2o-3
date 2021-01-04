package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria;
import hex.grid.HyperSpaceWalker.HyperSpaceIterator;
import hex.grid.HyperSpaceWalker.RandomDiscreteValueWalker;
import hex.schemas.TargetEncoderV3.TargetEncoderParametersV3;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.Job;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.api.GridSearchHandler.DefaultModelParametersBuilderFactory;
import water.fvec.Frame;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Enclosed.class)
public class TargetEncoderRGSTest{

  public static class TargetEncoderRGSNonParametrizedTest extends TestUtil {

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
        hpGrid.put("noise", new Double[]{0.0, 0.01, 0.1});
        hpGrid.put("inflection_point", new Double[]{1.0, 2.0, 3.0});
        hpGrid.put("smoothing", new Double[]{5.0, 10.0, 20.0});

        TargetEncoderParameters parameters = new TargetEncoderParameters();

        DefaultModelParametersBuilderFactory<TargetEncoderParameters, TargetEncoderParametersV3> modelParametersBuilderFactory = new DefaultModelParametersBuilderFactory<>();

        RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new RandomDiscreteValueSearchCriteria();
        RandomDiscreteValueWalker<TargetEncoderParameters> walker = new RandomDiscreteValueWalker<>(parameters, hpGrid, modelParametersBuilderFactory, hyperSpaceSearchCriteria);

        HyperSpaceIterator<TargetEncoderParameters> iterator = walker.iterator();
        int count = 0;
        while (iterator.hasNext()) {
          TargetEncoderParameters targetEncoderParameters = iterator.nextModelParameters();
          System.out.println(targetEncoderParameters._blending + ":" + targetEncoderParameters._noise + ":" + targetEncoderParameters._inflection_point + ":" + targetEncoderParameters._smoothing);
          count++;
        }
        assertEquals("Unexpected number of grid items", 54, count);
      } finally {
        Scope.exit();
      }
    }
  }

  @RunWith(Parameterized.class)
  public static class TargetEncoderRGSParametrizedTest extends TestUtil {

    @BeforeClass
    public static void setup() {
      stall_till_cloudsize(1);
    }

    @Parameterized.Parameters(name = "RGS over TE parameters: parallelism = {0}")
    public static Object[] parallelism() {
      return new Integer[]{1, 2, 4};
    }

    @Parameterized.Parameter
    public int parallelism;

    @Test
    public void regularGSOverTEParameters_parallel() {

      Scope.enter();
      try {
        Frame trainingFrame = parseTestFile("./smalldata/gbm_test/titanic.csv");
        Scope.track(trainingFrame);
        String responseColumn = "survived";
        asFactor(trainingFrame, responseColumn);

        HashMap<String, Object[]> hpGrid = new HashMap<>();
        hpGrid.put("blending", new Boolean[]{true, false});
        hpGrid.put("noise", new Double[]{0.0, 0.01,  0.1});
        hpGrid.put("inflection_point", new Double[]{1.0, 2.0, 3.0});
        hpGrid.put("smoothing", new Double[]{5.0, 10.0, 20.0});

        TargetEncoderParameters parameters = new TargetEncoderParameters();
        parameters._train = trainingFrame._key;
        parameters._response_column = responseColumn;
        parameters._ignored_columns = ignoredColumns(trainingFrame, "home.dest", "embarked", parameters._response_column);

        DefaultModelParametersBuilderFactory<TargetEncoderParameters, TargetEncoderParametersV3> modelParametersBuilderFactory = 
                new DefaultModelParametersBuilderFactory<>();

        RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new RandomDiscreteValueSearchCriteria();
        RandomDiscreteValueWalker<TargetEncoderParameters> walker = new RandomDiscreteValueWalker<>(
                parameters, 
                hpGrid, 
                modelParametersBuilderFactory, 
                hyperSpaceSearchCriteria
        );

        Job<Grid> gs = GridSearch.startGridSearch(Key.make(), walker, parallelism);

        Scope.track_generic(gs);
        final Grid grid = gs.get();
        Scope.track_generic(grid);

        assertEquals(54 /* 2*3*3*3 */, grid.getModelCount());
        assertTrue(Arrays.stream(grid.getModels()).allMatch(Objects::nonNull));
      } finally {
        Scope.exit();
      }
    }
  }
}
