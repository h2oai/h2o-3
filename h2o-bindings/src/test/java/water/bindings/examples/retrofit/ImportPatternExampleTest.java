package water.bindings.examples.retrofit;

import org.junit.Test;

import java.io.IOException;

public class ImportPatternExampleTest extends ExampleTestFixture {

    @Test
    public void testRun() throws IOException {
        ImportPatternExample.importPatternExample(getH2OUrl());
    }
}