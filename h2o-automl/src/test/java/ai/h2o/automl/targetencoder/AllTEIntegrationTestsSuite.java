package ai.h2o.automl.targetencoder;

import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@Ignore
@RunWith(Suite.class)
@Suite.SuiteClasses({
        AutoMLTargetEncodingAssistantTest.class,
        TEIntegrationWithAutoMLTest.class,
//        AllCategoricalTEApplicationStrategyTest.class, // TODO move test from targetencider ext module
        GridSearchTEParamsSelectionStrategyTest.class,
        TargetEncodingHyperparamsEvaluatorTest.class,
//        ThresholdTEApplicationStrategyTest.class  // TODO move test from targetencider ext module
})

public class AllTEIntegrationTestsSuite {
  // the class remains empty,
  // used only as a holder for the above annotations
}