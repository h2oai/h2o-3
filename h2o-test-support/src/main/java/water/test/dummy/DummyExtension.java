package water.test.dummy;

import water.AbstractH2OExtension;

public class DummyExtension extends AbstractH2OExtension {
  @Override
  public String getExtensionName() {
    return "dummy";
  }
  @Override
  public void init() {
    DummyModelParameters params = new DummyModelParameters();
    new DummyModelBuilder(params, true); // as a side effect DummyModelBuilder will be registered in a static field ModelBuilder.ALGOBASES
  }
}
