package hex.psvm;

import hex.psvm.psvm.*;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@Ignore // CI (Jenkins) uses a different approach, this is only for convenience in development
@RunWith(Suite.class)
@Suite.SuiteClasses({
        KernelTest.class,
        LLMatrixTest.class,
        MatrixUtilsTest.class,
        IncompleteCholeskyFactorizationTest.class,
        PrimalDualIPMTest.class,
        PSVMTest.class
})
public class PSVMTestSuite {
}
