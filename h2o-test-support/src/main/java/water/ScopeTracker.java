package water;

import org.junit.Ignore;
import org.junit.rules.ExternalResource;
import water.fvec.Frame;

import java.io.Serializable;

@Ignore // just a simple class that avoid the classic try-Scope.enter-finally-Scope.exit pattern
public class ScopeTracker extends ExternalResource implements Serializable {

  @Override
  protected void before() {
    Scope.enter();
  }

  @Override
  protected void after() {
    Scope.exit();
  }

  public final void track(Frame... frames) {
    Scope.track(frames);
  }

  @SuppressWarnings("unchecked")
  public final <T extends Keyed<T>> T track(T keyed) {
    return Scope.track_generic(keyed);
  }

}
