package water.junit;

import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

class FatalCrashListener extends RunListener {

  @Override
  public void testFailure(Failure failure) throws Exception {
    final Throwable exception = failure.getException();

    // ignore recoverable exceptions
    if (exception instanceof AssertionError) return;

    // any other causes JVM exit
    exception.printStackTrace();
    System.exit(-1);
  }
}
