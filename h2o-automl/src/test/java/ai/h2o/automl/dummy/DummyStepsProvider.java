package ai.h2o.automl.dummy;

import ai.h2o.automl.*;
import org.junit.Ignore;

import java.util.function.Function;
import java.util.function.Supplier;

@Ignore("utility class")
public class DummyStepsProvider implements ModelingStepsProvider<DummyStepsProvider.DummyModelSteps>,
        ModelParametersProvider<DummyModel.DummyModelParameters> {

    public Function<AutoML, DummyModelSteps> modelStepsFactory = DummyModelSteps::new;

    public static class DummyModelSteps extends ModelingSteps {
        
        public static final String NAME = "dummy";

        public ModelingStep[] defaultModels = new ModelingStep[0];
        public ModelingStep[] grids = new ModelingStep[0];
        public ModelingStep[] exploitation = new ModelingStep[0];

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
            return exploitation;
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
}
