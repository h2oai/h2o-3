package ai.h2o.automl;

import ai.h2o.automl.StepDefinition.Step;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.fvec.Frame;
import water.logging.LoggingLevel;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static water.TestUtil.parseTestFile;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class ModelingStepRegistryTest {

    private AutoML aml;
    private Frame fr;

    private static Set<String> sortedProviders;

    @BeforeClass
    public static void setup() {
        sortedProviders = new TreeSet<>(String::compareToIgnoreCase);
        sortedProviders.addAll(ModelingStepsRegistry.stepsByName.keySet());
    }

    @Before
    public void createAutoML() {
        fr = parseTestFile("./smalldata/logreg/prostate_train.csv");
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec();
        buildSpec.input_spec.training_frame = fr._key;
        buildSpec.input_spec.response_column = "CAPSULE";
        aml = new AutoML(buildSpec);
    }

    @After
    public void cleanupAutoML() {
        if(aml!=null) aml.delete();
        if(fr!=null) fr.delete();
    }


    @Test
    public void test_registration_of_default_step_providers() {
        assertEquals(7, ModelingStepsRegistry.stepsByName.size());
        assertEquals("Detected some duplicate registration", 7, new HashSet<>(ModelingStepsRegistry.stepsByName.values()).size());
        for (Algo algo: Algo.values()) {
            assertTrue(ModelingStepsRegistry.stepsByName.containsKey(algo.name()));
            assertNotNull(ModelingStepsRegistry.stepsByName.get(algo.name()));
        }
    }

    @Test
    public void test_empty_definition() {
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        assertEquals(0, registry.getOrderedSteps(new StepDefinition[0], aml).length);
    }

    @Test
    public void test_non_empty_definition() {
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        assertEquals(5, registry.getOrderedSteps(new StepDefinition[]{
                new StepDefinition(Algo.GBM.name(), StepDefinition.Alias.defaults)
        }, aml).length);
    }

    @Test
    public void test_all_registered_steps() {
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        List<StepDefinition> allSteps = sortedProviders.stream()
                .map(name -> new StepDefinition(name, StepDefinition.Alias.all))
                .collect(Collectors.toList());
        ModelingStep[] modelingSteps = registry.getOrderedSteps(allSteps.toArray(new StepDefinition[0]), aml);
        // 2 groups by default (1 for models, 1 for grids), hence the 2*2 SEs + 10 optional SEs
        assertEquals((1/*completion*/)+(1+3/*DL*/) + (2/*DRF*/) + (5+1+1/*GBM*/) + (1/*GLM*/) + (2*2+10/*SE*/) + (3+1+2/*XGB*/+1/*gblinear*/),
                modelingSteps.length);
        assertEquals(1, Stream.of(modelingSteps).filter(s -> "completion".equals(s.getProvider())).filter(ModelingStep.DynamicStep.class::isInstance).count());
        assertEquals(1, Stream.of(modelingSteps).filter(s -> Algo.DeepLearning.name().equals(s.getProvider())).filter(ModelingStep.ModelStep.class::isInstance).count());
        assertEquals(3, Stream.of(modelingSteps).filter(s -> Algo.DeepLearning.name().equals(s.getProvider())).filter(ModelingStep.GridStep.class::isInstance).count());
        assertEquals(2, Stream.of(modelingSteps).filter(s -> Algo.DRF.name().equals(s.getProvider())).filter(ModelingStep.ModelStep.class::isInstance).count());
        assertEquals(5, Stream.of(modelingSteps).filter(s -> Algo.GBM.name().equals(s.getProvider())).filter(ModelingStep.ModelStep.class::isInstance).count());
        assertEquals(1, Stream.of(modelingSteps).filter(s -> Algo.GBM.name().equals(s.getProvider())).filter(ModelingStep.GridStep.class::isInstance).count());
        assertEquals(1, Stream.of(modelingSteps).filter(s -> Algo.GBM.name().equals(s.getProvider())).filter(ModelingStep.SelectionStep.class::isInstance).count());
        assertEquals(1, Stream.of(modelingSteps).filter(s -> Algo.GLM.name().equals(s.getProvider())).filter(ModelingStep.ModelStep.class::isInstance).count());
        assertEquals(14, Stream.of(modelingSteps).filter(s -> Algo.StackedEnsemble.name().equals(s.getProvider())).filter(ModelingStep.ModelStep.class::isInstance).count());
        assertEquals(3, Stream.of(modelingSteps).filter(s -> Algo.XGBoost.name().equals(s.getProvider())).filter(ModelingStep.ModelStep.class::isInstance).count());
        assertEquals(2, Stream.of(modelingSteps).filter(s -> Algo.XGBoost.name().equals(s.getProvider())).filter(ModelingStep.GridStep.class::isInstance).count());
        assertEquals(2, Stream.of(modelingSteps).filter(s -> Algo.XGBoost.name().equals(s.getProvider())).filter(ModelingStep.SelectionStep.class::isInstance).count());

        List<String> orderedStepIds = Arrays.stream(modelingSteps).flatMap(s -> Stream.of(s._provider, s._id)).collect(Collectors.toList());
        // by default (reminder: this is not the default plan as defined in AutoML.defaultModelingPlan),
        // we should get all the default models first (from algos in lexicographic order as defined in sortedProvider) + the corresponding SEs (group 1),
        // followed by all grids (again in the same order) and the corresponding SEs (group 2),
        // followed by all selection steps and optional SEs (group 3)
        // followed by dynamic steps (group 100)
        assertEquals(Arrays.asList(
                Algo.DeepLearning.name(), "def_1",
                Algo.DRF.name(), "def_1", Algo.DRF.name(), "XRT",
                Algo.GBM.name(), "def_1", Algo.GBM.name(), "def_2", Algo.GBM.name(), "def_3", Algo.GBM.name(), "def_4", Algo.GBM.name(), "def_5",
                Algo.GLM.name(), "def_1",
                Algo.StackedEnsemble.name(), "best_of_family_1", Algo.StackedEnsemble.name(), "all_1",
                Algo.XGBoost.name(), "def_1", Algo.XGBoost.name(), "def_2", Algo.XGBoost.name(), "def_3",
                Algo.DeepLearning.name(), "grid_1", Algo.DeepLearning.name(), "grid_2", Algo.DeepLearning.name(), "grid_3",
                Algo.GBM.name(), "grid_1",
                Algo.StackedEnsemble.name(), "best_of_family_2", Algo.StackedEnsemble.name(), "all_2",
                Algo.XGBoost.name(), "grid_1", Algo.XGBoost.name(), "grid_gblinear",
                Algo.GBM.name(), "lr_annealing",
                Algo.StackedEnsemble.name(), "monotonic",
                Algo.StackedEnsemble.name(), "best_of_family", Algo.StackedEnsemble.name(), "all",
                Algo.StackedEnsemble.name(), "best_of_family_xgboost", Algo.StackedEnsemble.name(), "all_xgboost",
                Algo.StackedEnsemble.name(), "best_of_family_gbm", Algo.StackedEnsemble.name(), "all_gbm",
                Algo.StackedEnsemble.name(), "best_of_family_xglm", Algo.StackedEnsemble.name(), "all_xglm",
                Algo.StackedEnsemble.name(), "best_N",
                Algo.XGBoost.name(), "lr_annealing", Algo.XGBoost.name(), "lr_search",
                "completion", "resume_best_grids"
        ), orderedStepIds);


    }

    @Test
    public void test_all_default_models_without_SE() {
        StepDefinition[] allDefaultSteps = sortedProviders.stream()
                .filter(name -> !name.equals(Algo.StackedEnsemble.name()))
                .map(name -> new StepDefinition(name, StepDefinition.Alias.defaults))
                .toArray(StepDefinition[]::new);
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        ModelingStep[] modelingSteps = registry.getOrderedSteps(allDefaultSteps, aml);
        assertEquals((1/*DL*/) + (2/*DRF*/) + (5/*GBM*/) + (1/*GLM*/) + (3/*XGB*/),
                modelingSteps.length);
    }

    @Test
    public void test_all_default_models_with_SE() {
        StepDefinition[] allDefaultSteps = sortedProviders.stream()
                .map(name -> new StepDefinition(name, StepDefinition.Alias.defaults))
                .toArray(StepDefinition[]::new);
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        ModelingStep[] modelingSteps = registry.getOrderedSteps(allDefaultSteps, aml);
        assertEquals(2, Stream.of(modelingSteps).filter(ms -> ms.getAlgo() == Algo.StackedEnsemble).count());
        assertEquals((1/*DL*/) + (2/*DRF*/) + (5/*GBM*/) + (1/*GLM*/) + (2/*SE*/) + (3/*XGB*/),
                modelingSteps.length);
    }

    @Test
    public void test_all_grids() {
        StepDefinition[] allGridSteps = sortedProviders.stream()
                .map(name -> new StepDefinition(name, StepDefinition.Alias.grids))
                .toArray(StepDefinition[]::new);
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        ModelingStep[] modelingSteps = registry.getOrderedSteps(allGridSteps, aml);
        assertEquals((3/*DL*/) + (1/*GBM*/) + (1/*XGB*/+1/*gblinear*/),
                modelingSteps.length);
    }

    @Test
    public void test_all_defaults_plus_grids() {
        StepDefinition[] allGridSteps = sortedProviders.stream()
                .flatMap(name -> Stream.of(
                        new StepDefinition(name, StepDefinition.Alias.defaults),
                        new StepDefinition(name, StepDefinition.Alias.grids)
                ))
                .toArray(StepDefinition[]::new);
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        ModelingStep[] modelingSteps = registry.getOrderedSteps(allGridSteps, aml);
        // by default, 1 group for default models, 1 group for grids, hence the 2*2 SEs
        assertEquals((1+3/*DL*/) + (2/*DRF*/) + (5+1/*GBM*/) + (1/*GLM*/) + (2*2/*SE*/) + (3+1/*XGB*/+1/*gblinear*/),
                modelingSteps.length);
    }

    @Test
    public void test_registration_by_id() {
        StepDefinition[] byIdSteps = new StepDefinition[]{
                new StepDefinition(Algo.DRF.name(), "XRT"),  //group 1 by default
                new StepDefinition(Algo.XGBoost.name(), "grid_1"), // group 2 by default
                new StepDefinition(Algo.StackedEnsemble.name(), "best_of_family", "all") //group 3=2+1 by default
        };
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        ModelingStep[] modelingSteps = registry.getOrderedSteps(byIdSteps, aml);
        assertEquals(4, modelingSteps.length);
        assertEquals(Arrays.asList(1, 2, 3, 3), Arrays.stream(modelingSteps).map(s -> s._priorityGroup).collect(Collectors.toList()));
        assertEquals(Arrays.asList("XRT", "grid_1", "best_of_family", "all"), Arrays.stream(modelingSteps).map(s -> s._id).collect(Collectors.toList()));
        assertEquals(Arrays.asList(10, 30, 10, 10), Arrays.stream(modelingSteps).map(s -> s._weight).collect(Collectors.toList()));
    }

    @Test
    public void test_registration_with_groups() {
        StepDefinition[] byIdSteps = new StepDefinition[]{
                new StepDefinition(Algo.GBM.name(),
                        new Step("grid_1", 5, Step.DEFAULT_WEIGHT)),
                new StepDefinition(Algo.DRF.name(),
                        new Step("XRT", 1, Step.DEFAULT_WEIGHT)),
                new StepDefinition(Algo.XGBoost.name(),   //grids default to group 2
                        new Step("grid_1", Step.DEFAULT_GROUP, Step.DEFAULT_WEIGHT)),
                new StepDefinition(Algo.StackedEnsemble.name(), StepDefinition.Alias.defaults), //should generate 2 SEs for each previous group
                new StepDefinition(Algo.StackedEnsemble.name(), 
                        new Step("best_of_family", 7, 5),
                        new Step("monotonic", Step.DEFAULT_GROUP, Step.DEFAULT_WEIGHT) //should default to group 5+1
                )
        };
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        ModelingStep[] modelingSteps = registry.getOrderedSteps(byIdSteps, aml);
        assertEquals(11, modelingSteps.length);
        assertEquals(Arrays.asList(1, 1, 1, 2, 2, 2, 5, 5, 5, 6, 7), Arrays.stream(modelingSteps).map(s -> s._priorityGroup).collect(Collectors.toList()));
        assertEquals(Arrays.asList(
                "XRT", "best_of_family_1", "all_1",  //1
                "grid_1", "best_of_family_2", "all_2",  //2
                "grid_1", "best_of_family_5", "all_5",  //5
                "monotonic", //6
                "best_of_family" //7
                ), 
                Arrays.stream(modelingSteps).map(s -> s._id).collect(Collectors.toList()));
        assertEquals(Arrays.asList(10, 10, 10, 30, 10, 10, 30, 10, 10, 10, 5), Arrays.stream(modelingSteps).map(s -> s._weight).collect(Collectors.toList()));
    }


    @Test
    public void test_registration_with_weight() {
        StepDefinition[] withWeightSteps = new StepDefinition[]{
                new StepDefinition(Algo.DRF.name(), 
                        new Step("XRT", Step.DEFAULT_GROUP, 666)),
                new StepDefinition(Algo.GBM.name(), 
                        new Step("def_3", Step.DEFAULT_GROUP, 42),
                        new Step("grid_1", Step.DEFAULT_GROUP, 777))
        };
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        ModelingStep[] modelingSteps = registry.getOrderedSteps(withWeightSteps, aml);
        assertEquals(3, modelingSteps.length);
        assertEquals(Arrays.asList("XRT", "def_3", "grid_1"), Arrays.stream(modelingSteps).map(s -> s._id).collect(Collectors.toList()));
        assertEquals(Arrays.asList(666, 42, 777), Arrays.stream(modelingSteps).map(s -> s._weight).collect(Collectors.toList()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_unknown_provider_names_raise_error() {
        StepDefinition[] unknownProviderSteps = new StepDefinition[]{
                new StepDefinition("dummy", StepDefinition.Alias.all)
        };
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        ModelingStep[] modelingSteps = registry.getOrderedSteps(unknownProviderSteps, aml);
        assertEquals(0, modelingSteps.length);
    }

    @Test
    public void test_unknown_ids_are_skipped_with_warning() {
        StepDefinition[] unknownIdsSteps = new StepDefinition[]{
                new StepDefinition(Algo.GBM.name(), "dummy")
        };
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        ModelingStep[] modelingSteps = registry.getOrderedSteps(unknownIdsSteps, aml);
        assertEquals(0, modelingSteps.length);
        assertTrue(Stream.of(aml.eventLog()._events)
                .anyMatch(e ->
                        e.getLevel() == LoggingLevel.WARN
                        && e.getMessage().equals("Step 'dummy' not defined in provider 'GBM': skipping it.")));
    }

}
