package water;

import org.junit.Assert;

import org.junit.Test;
import org.junit.Ignore;

import java.io.Closeable;

/**
 * Created by michal on 10/10/15.
 */
public class Java6CompatibilityTest {

  static class OnCloseException extends RuntimeException {
  }

  static class TestException extends RuntimeException {
  }

  /**
   * Testing Throwable.addSuppressed code path.
   * See
   * http://stackoverflow.com/questions/7860137/what-is-the-java-7-try-with-resources-bytecode-equivalent-using-try-catch-finall
   *
   * <p>This test should pass on java7 but should fail on java6 since
   * there is no Throwable.addSuppressed method.</p>
   *
   * <p>The test trying to invoke path which includes Throwable.addSuppressed.
   * That means, in finally block during resource#close exception is thrown
   * and added into Throwable.addSuppressed.
   * </p>
   *
   */
  @Ignore
  @Test(expected = TestException.class)
  public void testTryWithResources() {

    class TestCloseableResource implements Closeable {
      boolean testCalled = false;
      boolean closedCalled = false;


      public void test() {
        testCalled = true;
        throw new TestException();
      }

      @Override
      public void close() {
        closedCalled = true;
        // Throw exception here to invoke right path in try-with-resource path
        throw new OnCloseException();
      }
    }

    TestCloseableResource referenceToResource = null;
    try(TestCloseableResource resource = new TestCloseableResource()) {
      referenceToResource = resource;
      resource.test();
    } catch (Throwable t) {
      Assert.assertEquals(true, referenceToResource.testCalled);
      Assert.assertEquals(true, referenceToResource.closedCalled);
      throw t;
    }
  }

}


