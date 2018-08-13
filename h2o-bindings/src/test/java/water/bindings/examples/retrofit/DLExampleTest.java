package water.bindings.examples.retrofit;

import org.junit.Test;

import java.io.IOException;

/**
 * Created by laurend on 6/1/18.
 */

public class DLExampleTest extends ExampleTestFixture {

    @Test
    public void testRun() throws IOException {
        DLExample.dlExampleFlow(getH2OUrl());
    }
}