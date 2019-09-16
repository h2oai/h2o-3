package ai.h2o.automl;

import ai.h2o.automl.StepDefinition.Step;
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

public class TrainingStepRegistryTest extends TestUtil {

    private AutoML aml;
    private Frame fr;

    private static Set<String> sortedProviders;

    @BeforeClass
    public static void setup() {
        stall_till_cloudsize(1);

        sortedProviders = new TreeSet<>(String::compareToIgnoreCase);
        sortedProviders.addAll(TrainingStepsRegistry.stepsByName.keySet());
    }

    @Before
    public void createAutoML() {
        fr = parse_test_file("./smalldata/logreg/prostate_train.csv");
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
        assertEquals(6, TrainingStepsRegistry.stepsByName.size());
        assertEquals("Detected some duplicate registration", 6, new HashSet<>(TrainingStepsRegistry.stepsByName.values()).size());
        for (Algo algo: Algo.values()) {
            assertTrue(TrainingStepsRegistry.stepsByName.containsKey(algo.name()));
            assertTrue(TrainingSteps.class.isAssignableFrom(TrainingStepsRegistry.stepsByName.get(algo.name())));
        }
    }

    @Test
    public void test_empty_definition() {
        TrainingStepsRegistry registry = new TrainingStepsRegistry(new StepDefinition[0]);
        assertEquals(0, registry.getOrderedSteps(aml).length);
    }

    @Test
    public void test_override_default_definition() {
        TrainingStepsRegistry registry = new TrainingStepsRegistry(new StepDefinition[0]);
        assertEquals(2, registry.getOrderedSteps(aml, new StepDefinition[]{
                new StepDefinition(Algo.StackedEnsemble.name(), StepDefinition.Alias.defaults)
        }).length);
    }

    @Test
    public void test_all_registered_steps() {
        TrainingStepsRegistry registry = new TrainingStepsRegistry(new StepDefinition[0]);
        List<StepDefinition> allSteps = sortedProviders.stream()
                .map(name -> new StepDefinition(name, StepDefinition.Alias.all))
                .collect(Collectors.toList());
        TrainingStep[] trainingSteps = registry.getOrderedSteps(aml, allSteps.toArray(new StepDefinition[0]));
        assertEquals((1 + 3/*DL*/) + (2/*DRF*/) + (5 + 1/*GBM*/) + (1/*GLM*/) + (2/*SE*/) + (3 + 1/*XGB*/),
                trainingSteps.length);
        assertEquals(1, Stream.of(trainingSteps).filter(s -> s._algo == Algo.DeepLearning).filter(TrainingStep.ModelStep.class::isInstance).count());
        assertEquals(3, Stream.of(trainingSteps).filter(s -> s._algo == Algo.DeepLearning).filter(TrainingStep.GridStep.class::isInstance).count());
        assertEquals(2, Stream.of(trainingSteps).filter(s -> s._algo == Algo.DRF).filter(TrainingStep.ModelStep.class::isInstance).count());
        assertEquals(5, Stream.of(trainingSteps).filter(s -> s._algo == Algo.GBM).filter(TrainingStep.ModelStep.class::isInstance).count());
        assertEquals(1, Stream.of(trainingSteps).filter(s -> s._algo == Algo.GBM).filter(TrainingStep.GridStep.class::isInstance).count());
        assertEquals(1, Stream.of(trainingSteps).filter(s -> s._algo == Algo.GLM).filter(TrainingStep.ModelStep.class::isInstance).count());
        assertEquals(2, Stream.of(trainingSteps).filter(s -> s._algo == Algo.StackedEnsemble).filter(TrainingStep.ModelStep.class::isInstance).count());
        assertEquals(3, Stream.of(trainingSteps).filter(s -> s._algo == Algo.XGBoost).filter(TrainingStep.ModelStep.class::isInstance).count());
        assertEquals(1, Stream.of(trainingSteps).filter(s -> s._algo == Algo.XGBoost).filter(TrainingStep.GridStep.class::isInstance).count());

        List<String> orderedStepIds = Arrays.stream(trainingSteps).map(s -> s._id).collect(Collectors.toList());
        assertEquals(Arrays.asList(
                "def_1", "grid_1", "grid_2", "grid_3",
                "def_1", "XRT",
                "def_1", "def_2", "def_3", "def_4", "def_5", "grid_1",
                "def_1",
                "best", "all",
                "def_1", "def_2", "def_3", "grid_1"
        ), orderedStepIds);


    }

    @Test
    public void test_all_default_models() {
        List<StepDefinition> allDefaultSteps = sortedProviders.stream()
                .map(name -> new StepDefinition(name, StepDefinition.Alias.defaults))
                .collect(Collectors.toList());
        TrainingStepsRegistry registry = new TrainingStepsRegistry(allDefaultSteps.toArray(new StepDefinition[0]));
        TrainingStep[] trainingSteps = registry.getOrderedSteps(aml);
        assertEquals((1/*DL*/) + (2/*DRF*/) + (5/*GBM*/) + (1/*GLM*/) + (2/*SE*/) + (3/*XGB*/),
                trainingSteps.length);
    }

    @Test
    public void test_all_grids() {
        List<StepDefinition> allGridSteps = sortedProviders.stream()
                .map(name -> new StepDefinition(name, StepDefinition.Alias.grids))
                .collect(Collectors.toList());
        TrainingStepsRegistry registry = new TrainingStepsRegistry(allGridSteps.toArray(new StepDefinition[0]));
        TrainingStep[] trainingSteps = registry.getOrderedSteps(aml);
        assertEquals((3/*DL*/) + (1/*GBM*/) + (1/*XGB*/),
                trainingSteps.length);
    }

    @Test
    public void test_registration_by_id() {
        List<StepDefinition> byIdSteps = Arrays.asList(
                new StepDefinition(Algo.DRF.name(), new String[] {"XRT"}),
                new StepDefinition(Algo.XGBoost.name(), new String[] {"grid_1"}),
                new StepDefinition(Algo.StackedEnsemble.name(), new String[] {"all", "best"})
        );
        TrainingStepsRegistry registry = new TrainingStepsRegistry(byIdSteps.toArray(new StepDefinition[0]));
        TrainingStep[] trainingSteps = registry.getOrderedSteps(aml);
        assertEquals(4, trainingSteps.length);
        assertEquals(Arrays.asList("XRT", "grid_1", "all", "best"), Arrays.stream(trainingSteps).map(s -> s._id).collect(Collectors.toList()));
        assertEquals(Arrays.asList(10, 100, 10, 10), Arrays.stream(trainingSteps).map(s -> s._weight).collect(Collectors.toList()));
    }

    @Test
    public void test_registration_with_weight() {
        List<StepDefinition> byIdSteps = Arrays.asList(
                new StepDefinition(Algo.DRF.name(), new Step[] { new Step("XRT", 666)}),
                new StepDefinition(Algo.GBM.name(), new Step[] { new Step("def_3", 42), new Step("grid_1", 777)})
        );
        TrainingStepsRegistry registry = new TrainingStepsRegistry(byIdSteps.toArray(new StepDefinition[0]));
        TrainingStep[] trainingSteps = registry.getOrderedSteps(aml);
        assertEquals(3, trainingSteps.length);
        assertEquals(Arrays.asList("XRT", "def_3", "grid_1"), Arrays.stream(trainingSteps).map(s -> s._id).collect(Collectors.toList()));
        assertEquals(Arrays.asList(666, 42, 777), Arrays.stream(trainingSteps).map(s -> s._weight).collect(Collectors.toList()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_unknown_provider_names_raise_error() {
        List<StepDefinition> byIdSteps = Arrays.asList(
                new StepDefinition("dummy", StepDefinition.Alias.all)
        );
        TrainingStepsRegistry registry = new TrainingStepsRegistry(byIdSteps.toArray(new StepDefinition[0]));
        TrainingStep[] trainingSteps = registry.getOrderedSteps(aml);
        assertEquals(0, trainingSteps.length);
    }

    @Test
    public void test_unknown_ids_are_skipped_with_warning() {
        List<StepDefinition> byIdSteps = Arrays.asList(
                new StepDefinition(Algo.GBM.name(), new String[] {"dummy"})
        );
        TrainingStepsRegistry registry = new TrainingStepsRegistry(byIdSteps.toArray(new StepDefinition[0]));
        TrainingStep[] trainingSteps = registry.getOrderedSteps(aml);
        assertEquals(0, trainingSteps.length);
        assertTrue(Stream.of(aml.eventLog()._events)
                .anyMatch(e ->
                        e.getLevel() == EventLogEntry.Level.Warn
                        && e.getMessage().equals("Step 'dummy' not defined in provider 'GBM': skipping it.")));
    }

}
