package ai.h2o.automl.targetencoding.integration;

import ai.h2o.automl.targetencoding.strategy.AllCategoricalTEApplicationStrategyTest;
import ai.h2o.automl.targetencoding.strategy.GridSearchTEParamsSelectionStrategyTest;
import ai.h2o.automl.targetencoding.strategy.TargetEncodingHyperparamsEvaluatorTest;
import ai.h2o.automl.targetencoding.strategy.ThresholdTEApplicationStrategyTest;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@Ignore
@RunWith(Suite.class)
@Suite.SuiteClasses({
        AutoMLTargetEncodingAssistantTest.class,
        TEIntegrationWithAutoMLTest.class,
        AllCategoricalTEApplicationStrategyTest.class,
        GridSearchTEParamsSelectionStrategyTest.class,
        TargetEncodingHyperparamsEvaluatorTest.class,
        ThresholdTEApplicationStrategyTest.class
})

public class AllTEIntegrationTestsSuite {
    // the class remains empty,
    // used only as a holder for the above annotations
}
