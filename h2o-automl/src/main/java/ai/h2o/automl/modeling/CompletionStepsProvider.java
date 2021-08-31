package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import ai.h2o.automl.ModelingStep.DynamicStep;
import ai.h2o.automl.WorkAllocations.Work;
import ai.h2o.automl.leaderboard.Leaderboard;
import hex.Model;
import hex.grid.Grid;
import hex.grid.HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria;
import water.Job;
import water.Key;

import java.util.*;
import java.util.stream.Collectors;

public class CompletionStepsProvider implements ModelingStepsProvider<CompletionStepsProvider.CompletionSteps> {

    public static class CompletionSteps extends ModelingSteps {

        static final String NAME = "completion";
        
        static class ResumingGridStep extends ModelingStep.GridStep {
            
            private transient GridStep _step;
            
            public ResumingGridStep(GridStep step, int priorityGroup, int weight, AutoML aml) {
                super(NAME, step.getAlgo(), step.getProvider()+"_"+step.getId(), priorityGroup, weight, aml);
                _work = makeWork();
                _step = step;
            }

            @Override
            public boolean canRun() {
                return _step != null && _weight > 0;
            }

            @Override
            public Model.Parameters prepareModelParameters() {
                return _step.prepareModelParameters();
            }

            @Override
            public Map<String, Object[]> prepareSearchParameters() {
                return _step.prepareSearchParameters();
            }

            @Override
            protected void setSearchCriteria(RandomDiscreteValueSearchCriteria searchCriteria, Model.Parameters baseParms) {
                super.setSearchCriteria(searchCriteria, baseParms);
                searchCriteria.set_stopping_rounds(0);
            }

            @Override
            @SuppressWarnings("unchecked")
            protected Job<Grid> startJob() {
                Key<Grid>[] resumedGrid = aml().session().getResumableKeys(_step.getProvider(), _step.getId());
                if (resumedGrid.length == 0) return null;
                return hyperparameterSearch(resumedGrid[0], prepareModelParameters(), prepareSearchParameters());
            }
        }
        
        static class ResumeBestNGridsStep extends DynamicStep<Model> {
            
            private final int _nGrids;
            
            public ResumeBestNGridsStep(String id, int nGrids, AutoML autoML) {
                super(NAME, id, autoML);
                _nGrids = nGrids;
            }
            
            private List<ModelingStep> sortModelingStepByPerf() {
                Map<ModelingStep, List<Double>> scoresBySource = new HashMap<>();
                Model[] models = getTrainedModels();
                double[] metrics = aml().leaderboard().getSortMetricValues();
                if (metrics == null) return Collections.emptyList();
                for (int i = 0; i < models.length; i++) {
                    ModelingStep source = aml().session().getModelingStep(models[i]._key);
                    if (!scoresBySource.containsKey(source)) {
                        scoresBySource.put(source, new ArrayList<>());
                    }
                    scoresBySource.get(source).add(metrics[i]);
                }
                Comparator<Map.Entry<ModelingStep, Double>> metricsComparator = Map.Entry.comparingByValue();
                if (!Leaderboard.isLossFunction(aml().leaderboard().getSortMetric())) metricsComparator = metricsComparator.reversed();
                return scoresBySource.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(-1)
                        ))
                        .entrySet().stream().sorted(metricsComparator)
                        .filter(e -> e.getValue() >= 0)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
            }

            @Override
            public ModelingStep[] prepareModelingSteps() {
                List<ModelingStep> bestStep = sortModelingStepByPerf();
                return bestStep.stream()
                        .filter(ModelingStep::isResumable)
                        .filter(GridStep.class::isInstance)
//                        .map(s -> aml().getModelingStep(s.getProvider(), s.getId()+"_resume"))
//                        .filter(Objects::nonNull)
                        .limit(_nGrids)
                        .map(s -> new ResumingGridStep((GridStep)s, _priorityGroup, _weight/_nGrids, aml()))
                        .toArray(ModelingStep[]::new);
                
            }
        }
        
        private final ModelingStep[] optionals = new ModelingStep[] {
                new ResumeBestNGridsStep("resume_best_grids", 2, aml())
        };
        
        public CompletionSteps(AutoML autoML) {
            super(autoML);
        }

        @Override
        public String getProvider() {
            return NAME;
        }

        @Override
        protected ModelingStep[] getOptionals() {
            return optionals;
        }
    }
    
    @Override
    public String getName() {
        return CompletionSteps.NAME;
    }

    @Override
    public CompletionSteps newInstance(AutoML aml) {
        return new CompletionSteps(aml);
    }

}
