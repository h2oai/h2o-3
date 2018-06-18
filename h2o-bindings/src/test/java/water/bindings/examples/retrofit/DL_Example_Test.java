package water.bindings.examples.retrofit;

import org.junit.Test;

import java.io.IOException;

/**
 * Created by laurend on 6/1/18.
 */

public class DL_Example_Test extends ExampleTestFixture {

    @Test
    public void testRun() throws IOException {
        DL_Example.dlExampleFlow(getH2OUrl());
    }
}