package water.bindings.examples.retrofit;

import org.junit.Test;

import java.io.IOException;

public class Merge_Example_Test extends ExampleTestFixture {

  @Test
  public void testRun() throws IOException {
    Merge_Example.mergeExample(getH2OUrl());
  }
}