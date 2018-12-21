package water;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.*;

public class RemoteRunnableTest extends TestUtil {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @BeforeClass
  static public void setup() {
    stall_till_cloudsize(5);
  }

  @Test
  public void testRunOnH2ONode_local() {
    DummyLocalRunnable runnable = new DummyLocalRunnable("NOT OK");
    DummyLocalRunnable result = H2O.runOnH2ONode(H2O.SELF, runnable);
    assertSame(runnable, result);
    assertSame(Thread.currentThread(), result._thread);
    assertEquals("OK", result._result);
  }

  @Test
  public void testRunOnH2ONode_remote() {
    H2ONode node = findRemoteNode();
    assertNotNull(node);
    DummyRemoteRunnable runnable = new DummyRemoteRunnable("NOT OK");
    DummyRemoteRunnable result = H2O.runOnH2ONode(node, runnable);
    assertNotSame(runnable, result);
    assertEquals("OK", result._result);
  }

  @Test
  public void testRunOnH2ONode_remoteFailing() {
    H2ONode node = findRemoteNode();
    assertNotNull(node);
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Failing intentionally");
    FailingRemoteRunnable runnable = new FailingRemoteRunnable();
    H2O.runOnH2ONode(node, runnable);
  }

  private H2ONode findRemoteNode() {
    for (H2ONode n : H2O.CLOUD.members()) {
      if (n != H2O.SELF) {
        return n;
      }
    }
    throw new IllegalStateException("No remote node found, possibly a single node cluster, size=" + H2O.CLOUD.size());
  }

  private static class DummyLocalRunnable extends H2O.RemoteRunnable<DummyLocalRunnable> {
    private String _result;
    private transient Thread _thread;
    public DummyLocalRunnable(String result) {
      _result = result;
    }
    @Override
    public void setupOnRemote() {
      throw new IllegalStateException("Shouldn't be called");
    }
    @Override
    public void run() {
      _thread = Thread.currentThread();
      _result = "OK";
    }
  }

  private static class DummyRemoteRunnable extends H2O.RemoteRunnable<DummyRemoteRunnable> {
    private String _result;
    public DummyRemoteRunnable(String result) {
      _result = result;
    }
    @Override
    public void setupOnRemote() {
      _result = "O";
    }
    @Override
    public void run() {
      _result += "K";
    }
  }

  private static class FailingRemoteRunnable extends H2O.RemoteRunnable<FailingRemoteRunnable> {
    @Override
    public void run() {
      throw new IllegalStateException("Failing intentionally");
    }
  }

}
