package water.parser;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.Request;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import water.H2O;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.api.ImportFilesHandler;
import water.api.RequestServer;
import water.fvec.Frame;

public class ClientParserTest extends TestUtil {
  //
  // BIG WARNING: this test is not executed in client mode if it is run
  // under regular H2O test infrastructure. See [PUBDEV-2643]
  //
  @BeforeClass static public void setup() {
    stall_till_cloudsize(new String[] {"-client"}, 4);
  }

  @Test public void testBasic() throws IOException {
    Scope.enter();
    String[] files = new String[] { "smalldata/iris/multiple_iris_files_wheader/iris1.csv",
                                  "smalldata/chicago/chicagoCensus.csv",
                                  "smalldata/chicago/chicagoCrimes10k.csv.zip"};
    try {
      for (String f : files) {
        Frame frame = parse_test_file(Key.make("data_1_"+f), f);
        frame.delete();
      }
    } finally {
      Scope.exit();
    }
  }
}
