package ai.h2o.automl.targetencoding;


import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@Ignore // just for the case if Jenkins is picking up this class causing tests to run two times
@RunWith(Suite.class)
@Suite.SuiteClasses({
        TargetEncodingTest.class,
        TargetEncodingKFoldStrategyTest.class,
        TargetEncodingLeaveOneOutStrategyTest.class,
        TargetEncodingNoneStrategyTest.class,
        TargetEncodingMultiClassTargetTest.class,
        TargetEncodingTargetColumnTest.class,
        TargetEncodingExceptionsHandlingTest.class,
        TargetEncodingFrameHelperTest.class,
        TargetEncodingImmutabilityTest.class
})

public class AllTETestsSuite {
    // the class remains empty,
    // used only as a holder for the above annotations
}