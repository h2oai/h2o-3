package ai.h2o.automl;

import ai.h2o.automl.StepDefinition.Step;
import ai.h2o.automl.events.EventLogEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class ModelingStepRegistryTest extends TestUtil {

    private AutoML aml;
    private Frame fr;

    private static Set<String> sortedProviders;

    @BeforeClass
    public static void setup() {
        stall_till_cloudsize(1);

        sortedProviders = new TreeSet<>(String::compareToIgnoreCase);
        sortedProviders.addAll(ModelingStepsRegistry.stepsByName.keySet());
    }

    @Before
    public void createAutoML() {
        fr = parseTestFile("./smalldata/logreg/prostate_train.csv");
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec();
        buildSpec.input_spec.training_frame = fr._key;
        buildSpec.input_spec.response_column = "CAPSULE";
        aml = new AutoML(null, new Date(), buildSpec);
    }

    @After
    public void cleanupAutoML() {
        if(aml!=null) aml.delete();
        if(fr!=null) fr.delete();
    }


    @Test
    public void test_registration_of_default_step_providers() {
        assertEquals(6, ModelingStepsRegistry.stepsByName.size());
        assertEquals("Detected some duplicate registration", 6, new HashSet<>(ModelingStepsRegistry.stepsByName.values()).size());
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
        assertEquals(3, registry.getOrderedSteps(new StepDefinition[]{
                new StepDefinition(Algo.StackedEnsemble.name(), StepDefinition.Alias.defaults)
        }, aml).length);
    }

    @Test
    public void test_all_registered_steps() {
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        List<StepDefinition> allSteps = sortedProviders.stream()
                .map(name -> new StepDefinition(name, StepDefinition.Alias.all))
                .collect(Collectors.toList());
        ModelingStep[] modelingSteps = registry.getOrderedSteps(allSteps.toArray(new StepDefinition[0]), aml);
        assertEquals((1 + 3/*DL*/) + (2/*DRF*/) + (5 + 1 + 1/*GBM*/) + (1/*GLM*/) + (3/*SE*/) + (3 + 1 + 2/*XGB*/),
                modelingSteps.length);
        assertEquals(1, Stream.of(modelingSteps).filter(s -> s._algo == Algo.DeepLearning).filter(ModelingStep.ModelStep.class::isInstance).count());
        assertEquals(3, Stream.of(modelingSteps).filter(s -> s._algo == Algo.DeepLearning).filter(ModelingStep.GridStep.class::isInstance).count());
        assertEquals(2, Stream.of(modelingSteps).filter(s -> s._algo == Algo.DRF).filter(ModelingStep.ModelStep.class::isInstance).count());
        assertEquals(5, Stream.of(modelingSteps).filter(s -> s._algo == Algo.GBM).filter(ModelingStep.ModelStep.class::isInstance).count());
        assertEquals(1, Stream.of(modelingSteps).filter(s -> s._algo == Algo.GBM).filter(ModelingStep.GridStep.class::isInstance).count());
        assertEquals(1, Stream.of(modelingSteps).filter(s -> s._algo == Algo.GBM).filter(ModelingStep.SelectionStep.class::isInstance).count());
        assertEquals(1, Stream.of(modelingSteps).filter(s -> s._algo == Algo.GLM).filter(ModelingStep.ModelStep.class::isInstance).count());
        assertEquals(3, Stream.of(modelingSteps).filter(s -> s._algo == Algo.StackedEnsemble).filter(ModelingStep.ModelStep.class::isInstance).count());
        assertEquals(3, Stream.of(modelingSteps).filter(s -> s._algo == Algo.XGBoost).filter(ModelingStep.ModelStep.class::isInstance).count());
        assertEquals(1, Stream.of(modelingSteps).filter(s -> s._algo == Algo.XGBoost).filter(ModelingStep.GridStep.class::isInstance).count());
        assertEquals(2, Stream.of(modelingSteps).filter(s -> s._algo == Algo.XGBoost).filter(ModelingStep.SelectionStep.class::isInstance).count());

        List<String> orderedStepIds = Arrays.stream(modelingSteps).map(s -> s._id).collect(Collectors.toList());
        assertEquals(Arrays.asList(
                "def_1", "grid_1", "grid_2", "grid_3",
                "def_1", "XRT",
                "def_1", "def_2", "def_3", "def_4", "def_5", "grid_1", "lr_annealing",
                "def_1",
                "best", "all", "monotonic",
                "def_1", "def_2", "def_3", "grid_1", "lr_annealing", "lr_search"
        ), orderedStepIds);


    }

    @Test
    public void test_all_default_models() {
        StepDefinition[] allDefaultSteps = sortedProviders.stream()
                .map(name -> new StepDefinition(name, StepDefinition.Alias.defaults))
                .toArray(StepDefinition[]::new);
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        ModelingStep[] modelingSteps = registry.getOrderedSteps(allDefaultSteps, aml);
        assertEquals((1/*DL*/) + (2/*DRF*/) + (5/*GBM*/) + (1/*GLM*/) + (3/*SE*/) + (3/*XGB*/),
                modelingSteps.length);
    }

    @Test
    public void test_all_grids() {
        StepDefinition[] allGridSteps = sortedProviders.stream()
                .map(name -> new StepDefinition(name, StepDefinition.Alias.grids))
                .toArray(StepDefinition[]::new);
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        ModelingStep[] modelingSteps = registry.getOrderedSteps(allGridSteps, aml);
        assertEquals((3/*DL*/) + (1/*GBM*/) + (1/*XGB*/),
                modelingSteps.length);
    }

    @Test
    public void test_registration_by_id() {
        StepDefinition[] byIdSteps = new StepDefinition[]{
                new StepDefinition(Algo.DRF.name(), new String[]{"XRT"}),
                new StepDefinition(Algo.XGBoost.name(), new String[]{"grid_1"}),
                new StepDefinition(Algo.StackedEnsemble.name(), new String[]{"all", "best"})
        };
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        ModelingStep[] modelingSteps = registry.getOrderedSteps(byIdSteps, aml);
        assertEquals(4, modelingSteps.length);
        assertEquals(Arrays.asList("XRT", "grid_1", "all", "best"), Arrays.stream(modelingSteps).map(s -> s._id).collect(Collectors.toList()));
        assertEquals(Arrays.asList(10, 100, 10, 10), Arrays.stream(modelingSteps).map(s -> s._weight).collect(Collectors.toList()));
    }

    @Test
    public void test_registration_with_weight() {
        StepDefinition[] withWeightSteps = new StepDefinition[]{
                new StepDefinition(Algo.DRF.name(), new Step[] { new Step("XRT", 666)}),
                new StepDefinition(Algo.GBM.name(), new Step[] { new Step("def_3", 42), new Step("grid_1", 777)})
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
                new StepDefinition(Algo.GBM.name(), new String[] {"dummy"})
        };
        ModelingStepsRegistry registry = new ModelingStepsRegistry();
        ModelingStep[] modelingSteps = registry.getOrderedSteps(unknownIdsSteps, aml);
        assertEquals(0, modelingSteps.length);
        assertTrue(Stream.of(aml.eventLog()._events)
                .anyMatch(e ->
                        e.getLevel() == EventLogEntry.Level.Warn
                        && e.getMessage().equals("Step 'dummy' not defined in provider 'GBM': skipping it.")));
    }

}
