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

  @SuppressWarnings("unchecked")
  public final <T extends Keyed> T track(Keyed<T> keyed) {
    Scope.track_generic(keyed);
    return (T) keyed;
  }

}
