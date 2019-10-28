package hex.grid;

import hex.ModelBuilderTest;
import org.junit.Ignore;
import org.junit.Test;
import water.Key;
import water.TestUtil;

import java.util.HashMap;

public class HyperSpaceWalkerTest extends TestUtil {
  
  
  // Tests for MaterializedRandomWalker
  
  @Test
  public void getCurrentRawParameters() {
    //TODO check that in case of error we will return current parameters
  }
  
  @Ignore //TODO  Register DummyModelParameters and enable the test
  public void seedEnsuresReproducibilityForMaterialisedRandomWalker() {

    HashMap<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_nfolds", new Integer[]{3, 5, 10});
    hpGrid.put("_auto_rebalance", new Boolean[]{false, true});
    

    ModelBuilderTest.DummyModelParameters parameters = new ModelBuilderTest.DummyModelParameters("Seed test", Key.make( "seed-test"));
    GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();
    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

    HyperSpaceWalker.MaterializedRandomWalker<ModelBuilderTest.DummyModelParameters> walker =
            new HyperSpaceWalker.MaterializedRandomWalker<ModelBuilderTest.DummyModelParameters>(parameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria,
                    null);

    HyperSpaceWalker.HyperSpaceIterator<ModelBuilderTest.DummyModelParameters> iterator = walker.iterator();
    while (iterator.hasNext(null)) {
      ModelBuilderTest.DummyModelParameters targetEncoderParameters = iterator.nextModelParameters(null);
    }
  }
  
  
}
