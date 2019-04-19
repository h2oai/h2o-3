package water;

import org.junit.Ignore;
import org.junit.rules.ExternalResource;
import water.fvec.Frame;

@Ignore // just a simple class that avoid the classic try-Scope.enter-finally-Scope.exit pattern
public class ScopeTracker extends ExternalResource {

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

  public final <E extends Keyed<E>> void track(E keyed) {
    Scope.track_generic(keyed);
  }

}
