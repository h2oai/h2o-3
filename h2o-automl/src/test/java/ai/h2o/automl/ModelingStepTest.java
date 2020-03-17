package ai.h2o.automl;

import hex.grid.Grid;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Job;
import water.Key;
import water.runner.CloudSize;
import water.runner.H2ORunner;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class ModelingStepTest {

    @Test
    public void testModelStep() {

    }

    @Test public void testGridStep() {

    }

    @Test public void testSelectionStep() {

    }


    private static class DummyModelStep extends ModelingStep.ModelStep {
        public DummyModelStep(Algo algo, String id, int cost, AutoML autoML) {
            super(algo, id, cost, autoML);
        }

        @Override
        protected Job startJob() {
            return null;
        }
    }

    private static class DummyGRidStep extends ModelingStep.GridStep {
        public DummyGRidStep(Algo algo, String id, int cost, AutoML autoML) {
            super(algo, id, cost, autoML);
        }

        @Override
        protected Job<Grid> startJob() {
            return null;
        }
    }

    private static class DummySelectionStep extends ModelingStep.SelectionStep {
        public DummySelectionStep(Algo algo, String id, int weight, AutoML autoML) {
            super(algo, id, weight, autoML);
        }

        @Override
        protected Job<Models> startTraining(Key result, double maxRuntimeSecs) {
            return null;
        }

        @Override
        protected ModelSelectionStrategy getSelectionStrategy() {
            return null;
        }
    }
}
