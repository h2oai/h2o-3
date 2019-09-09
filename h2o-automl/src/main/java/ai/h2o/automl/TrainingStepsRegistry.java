package ai.h2o.automl;

import hex.Model;
import water.DKV;
import water.Iced;
import water.Key;
import water.nbhm.NonBlockingHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;

public class TrainingStepsRegistry extends Iced<TrainingStepsRegistry> {

    private static NonBlockingHashMap<String, Class<TrainingSteps>> stepsByName = new NonBlockingHashMap<>();

    static {
        ServiceLoader<TrainingStepsProvider> trainingStepsProviders = ServiceLoader.load(TrainingStepsProvider.class);
        for (TrainingStepsProvider provider : trainingStepsProviders) {
            stepsByName.put(provider.getName(), provider.getStepsClass());
        }
    }

    private static class StepsDescription extends Iced<StepsDescription> {

        private String _name;
        private String _alias;
        private String[] _ids;

        public StepsDescription(String name, String alias) {
            _name = name;
            _alias = alias;
        }

        public StepsDescription(String name, String[] ids) {
            _name = name;
            _ids = ids;
        }
    }

    private static StepsDescription[] defaultExecutionOrder = {
            new StepsDescription(Algo.XGBoost.name(), "defaults"),
            new StepsDescription(Algo.GLM.name(), "defaults"),
            new StepsDescription(Algo.DRF.name(), new String[]{ "def_1" }),
            new StepsDescription(Algo.GBM.name(), "defaults"),
            new StepsDescription(Algo.DeepLearning.name(), "defaults"),
            new StepsDescription(Algo.DRF.name(), new String[]{ "XRT" }),
            new StepsDescription(Algo.XGBoost.name(), "grids"),
            new StepsDescription(Algo.GBM.name(), "grids"),
            new StepsDescription(Algo.DeepLearning.name(), "grids"),
            new StepsDescription(Algo.StackedEnsemble.name(), "defaults"),
    };

    private Key<AutoML> _amlKey;

    public TrainingStepsRegistry(AutoML aml) {
        _amlKey = aml._key;
    }

    public TrainingStep[] getOrderedSteps() {
        return getOrderedSteps(defaultExecutionOrder);
    }

    public TrainingStep[] getOrderedSteps(StepsDescription[] executionOrder) {
        List<TrainingStep> orderedSteps = new ArrayList<>();
        for (StepsDescription descr : executionOrder) {
            Class<TrainingSteps> clazz = stepsByName.get(descr._name);
            if (clazz == null) {
                // log
                continue;
            }
            try {
                TrainingSteps steps = clazz.getConstructor(AutoML.class).newInstance(aml());
                if (descr._alias != null) {
                    orderedSteps.addAll(Arrays.asList(steps.getSteps(descr._alias)));
                } else if (descr._ids != null) {
                    orderedSteps.addAll(Arrays.asList(steps.getSteps(descr._ids)));
                }
            } catch (Exception e) {
                //log
                continue;
            }
        }
        return orderedSteps.toArray(new TrainingStep[0]);
    }

    public final AutoML aml() {
        return DKV.getGet(_amlKey);
    }

}
