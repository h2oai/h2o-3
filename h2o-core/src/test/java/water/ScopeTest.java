package water;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class ScopeTest {

  @Test
  public void testTrackGeneric() {
    final Key<DummyKeyed> k = Key.make();
    try {
      Scope.enter();
      DKV.put(Scope.track_generic(new DummyKeyed(k)));
      Scope.exit();
      assertNull("DKV value should be automatically removed", DKV.get(k));
    } finally {
      DKV.remove(k);
    }
  }

  private static final class DummyKeyed extends Keyed<DummyKeyed> {
    public DummyKeyed() {
      super();
    }
    private DummyKeyed(Key<DummyKeyed> key) {
      super(key);
    }
  }

}
