package ai.h2o.targetencoding;


import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@Ignore // just for the case if Jenkins is picking up this class causing tests to run two times
@RunWith(Suite.class)
@Suite.SuiteClasses({
        TargetEncoderModelTest.class,
        TargetEncodingKFoldStrategyTest.class,
        TargetEncodingLeaveOneOutStrategyTest.class,
        TargetEncodingNoneStrategyTest.class,
        TargetEncodingOnBinaryTest.class,
        TargetEncodingOnMulticlassTest.class,
        TargetEncodingOnRegressionTest.class,
        TargetEncodingExceptionsHandlingTest.class,
        TargetEncodingHelperTest.class,
        TargetEncodingImmutabilityTest.class,
        TargetEncoderBroadcastJoinTest.class,
        TargetEncodingImmutabilityTest.class,
        TargetEncoderMojoIntegrationTest.class,
        TargetEncoderTest.class,
        TargetEncoderMojoWriterTest.class,
        TargetEncoderRGSTest.class
})

public class TargetEncoderTestSuite {
    // the class remains empty,
    // used only as a holder for the above annotations
}
