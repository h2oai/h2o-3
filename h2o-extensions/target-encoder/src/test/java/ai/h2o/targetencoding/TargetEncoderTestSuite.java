package ai.h2o.targetencoding;


import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@Ignore // just for the case if Jenkins is picking up this class causing tests to run two times
@RunWith(Suite.class)
@Suite.SuiteClasses({
        TargetEncodingTest.class,
        TargetEncoderModelTest.class,
        TargetEncodingKFoldStrategyTest.class,
        TargetEncodingLeaveOneOutStrategyTest.class,
        TargetEncodingNoneStrategyTest.class,
        TargetEncodingMultiClassTargetTest.class,
        TargetEncodingTargetColumnTest.class,
        TargetEncodingExceptionsHandlingTest.class,
        TargetEncodingFrameHelperTest.class,
        TargetEncodingImmutabilityTest.class,
        BroadcastJoinTest.class,
        TargetEncodingImmutabilityTest.class,
        TEMojoIntegrationTest.class,
        TargetEncoderBuilderTest.class,
        TargetEncoderMojoWriterTest.class,
        TargetEncoderRGSTest.class
})

public class TargetEncoderTestSuite {
    // the class remains empty,
    // used only as a holder for the above annotations
}
