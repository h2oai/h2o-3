package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import ai.h2o.automl.ModelingStep.DynamicStep;
import ai.h2o.automl.leaderboard.Leaderboard;
import hex.Model;

import java.util.*;
import java.util.stream.Collectors;

public class CompletionStepsProvider implements ModelingStepsProvider<CompletionStepsProvider.CompletionSteps> {

    public static class CompletionSteps extends ModelingSteps {

        static final String NAME = "completion";
        
        static class FinalGridStep extends DynamicStep<Model> {
            public FinalGridStep(String id, int weight, int priorityGroup, AutoML autoML) {
                super(NAME, id, weight, priorityGroup, autoML);
            }
            
            private List<ModelingStep> sortModelingStepByPerf() {
                Map<ModelingStep, List<Double>> scoresBySource = new HashMap<>();
                Model[] models = getTrainedModels();
                double[] metrics = aml().leaderboard().getSortMetricValues();
                if (metrics == null) return Collections.emptyList();
                for (int i = 0; i < models.length; i++) {
                    ModelingStep source = aml().getModelingStep(models[i]._key);
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
            protected ModelingStep[] prepareModelingSteps() {
                List<ModelingStep> bestStep = sortModelingStepByPerf();
                return bestStep.stream()
                        .filter(ModelingStep::isResumable)
                        .map(s -> aml().getModelingStep(s.getProvider(), s.getId()+"_resume"))
                        .filter(Objects::nonNull)
                        .limit(2)
                        .toArray(ModelingStep[]::new);
            }
        }
        

        public CompletionSteps(AutoML autoML) {
            super(autoML);
        }

        @Override
        public String getProvider() {
            return NAME;
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
