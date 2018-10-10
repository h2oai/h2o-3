package ai.h2o.automl.targetencoding;


import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        TargetEncodingTest.class,
        TargetEncodingKFoldStrategyTest.class,
        TargetEncodingLeaveOneOutStrategyTest.class,
        TargetEncodingNoneStrategyTest.class,
        TargetEncodingImmutabilityTest.class
})

public class AllTETestsSuite {
    // the class remains empty,
    // used only as a holder for the above annotations
}