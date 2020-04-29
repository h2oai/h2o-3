package water.test.dummy;

import water.Iced;

public abstract class DummyAction<T> extends Iced<DummyAction<T>> {
  protected abstract String run(DummyModelParameters parms);
  protected void cleanUp() {};
}
