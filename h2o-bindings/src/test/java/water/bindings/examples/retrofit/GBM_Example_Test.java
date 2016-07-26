package water.bindings.examples.retrofit;

import org.junit.Test;

import java.io.IOException;

public class GBM_Example_Test extends ExampleTestFixture {

  @Test
  public void testRun() throws IOException {
    GBM_Example.gbmExampleFlow(getH2OUrl());
  }
}