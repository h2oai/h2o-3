package ai.h2o.automl.dummy;

import ai.h2o.automl.*;
import hex.Model;
import org.junit.Ignore;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Ignore("utility class")
public class DummyStepsProvider implements ModelingStepsProvider<DummyStepsProvider.DummyModelSteps>,
        ModelParametersProvider<DummyModel.DummyModelParameters> {

    public Function<AutoML, DummyModelSteps> modelStepsFactory = DummyModelSteps::new;

    public static class DummyModelSteps extends ModelingSteps {
        
        public static final String NAME = "dummy";

        public ModelingStep[] defaultModels = new ModelingStep[0];
        public ModelingStep[] grids = new ModelingStep[0];
        public ModelingStep[] optionals = new ModelingStep[0];

        public DummyModelSteps(AutoML autoML) {
            super(autoML);
        }

        @Override
        public String getProvider() {
            return NAME;
        }

        @Override
        protected ModelingStep[] getDefaultModels() {
            return defaultModels;
        }

        @Override
        protected ModelingStep[] getGrids() {
            return grids;
        }

        @Override
        protected ModelingStep[] getOptionals() {
            return optionals;
        }
    }

    @Override
    public String getName() {
        return DummyModelSteps.NAME;
    }

    @Override
    public DummyModelSteps newInstance(AutoML aml) {
        return modelStepsFactory.apply(aml);
    }

    @Override
    public DummyModel.DummyModelParameters newDefaultParameters() {
        return new DummyModel.DummyModelParameters();
    }

    
    public static class DummyModelStep extends ModelingStep.ModelStep<DummyModel> {
        
        public DummyModelStep(IAlgo algo, String id, AutoML autoML) {
            this(algo, id, false, autoML);
        }

        public DummyModelStep(IAlgo algo, String id, boolean dynamic, AutoML autoML) {
            super(DummyModelSteps.NAME, algo, id, autoML);
            if (dynamic) _work = makeWork();
        }

        @Override
        public Model.Parameters prepareModelParameters() {
            return new DummyModel.DummyModelParameters();
        }
    }

    public static class DummyGridStep extends ModelingStep.GridStep<DummyModel> {

        public DummyGridStep(IAlgo algo, String id, AutoML autoML) {
            super(DummyModelSteps.NAME, algo, id, autoML);
        }

        @Override
        public Model.Parameters prepareModelParameters() {
            return new DummyModel.DummyModelParameters();
        }

        @Override
        public Map<String, Object[]> prepareSearchParameters() {
            Map<String, Object[]> searchParams = new HashMap<>();
            searchParams.put("_tag", new String[] {"one", "two", "three"});
            return searchParams;
          }
      }
  }
