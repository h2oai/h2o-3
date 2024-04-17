package water.junit.rules;

import org.junit.Ignore;
import org.junit.rules.ExternalResource;
import water.Keyed;
import water.Scope;
import water.fvec.Frame;

import java.io.Serializable;

@Ignore // just a simple class that avoid the classic try-Scope.enter-finally-Scope.exit pattern
public class ScopeTracker extends ExternalResource implements Serializable {

  @Override
  protected void before() {
    Scope.reset();
    Scope.enter();
  }

  @Override
  protected void after() {
    Scope.exit();
    assert Scope.nLevel() == 0: "at least one nested Scope was not exited properly: "+Scope.nLevel();
  }

  public final Frame track(Frame frame) { // no varargs (no use in tests) as the Java compiler is misleading: when calling `track(fr)` it prefers the signature with generic to the signature with Frame varargs.
    return Scope.track(frame);
  }

  @SuppressWarnings("unchecked")
  public final <T extends Keyed<T>> T track(T keyed) {
    return Scope.track_generic(keyed);
  }

}
