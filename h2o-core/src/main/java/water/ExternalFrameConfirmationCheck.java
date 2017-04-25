package water;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A class used for checking whether read/write operation in external cluster finished within specified timeout
 */
public class ExternalFrameConfirmationCheck extends H2O.H2OCountedCompleter{
    private AutoBuffer ab;
    private byte confirmByte;
    public ExternalFrameConfirmationCheck(AutoBuffer ab){
        this.ab = ab;
    }
    @Override
    public void compute2() {
        // confirmAb.get1() forces this code to wait for result and
        // forces all the previous work to be done on the h2o node side.
        confirmByte = ab.get1();
        tryComplete();
    }

    public static byte getConfirmation(AutoBuffer ab, long timeout) throws InterruptedException, TimeoutException, ExecutionException {
        ExternalFrameConfirmationCheck confirmationCheck = water.H2O.submitTask(new ExternalFrameConfirmationCheck(ab));
        confirmationCheck.get(timeout, TimeUnit.SECONDS);
        return confirmationCheck.confirmByte;
    }
}
