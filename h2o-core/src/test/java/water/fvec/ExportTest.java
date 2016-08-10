package water.fvec;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.*;
import water.parser.ParseDataset;
import water.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExportTest extends TestUtil {

  @Rule
  public TemporaryFolder tmpFolder = new TemporaryFolder();

  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test public void testExport() throws IOException {
    Frame fr = parse_test_file("smalldata/airlines/airlineUUID.csv");
    Key rebalancedKey = Key.make("rebalanced");
    Frame rebalanced = null;
    Frame imported = null;
    int[] partSpec = {1, 4, 7, 30, -1};
    int[] expPart = {1, 4, 6, 17, -1};
    for (int i = 0; i < partSpec.length; i++) {
      Log.info("Testing export to " + partSpec[i] + " files.");
      try {
        int parts = partSpec[i];
        Scope.enter();
        rebalanced = rebalance(fr, rebalancedKey, 17);
        File folder = tmpFolder.newFolder("export_" + parts);
        File target = (parts == 1) ? new File(folder, "data.csv") : folder;
        Log.info("Should output #" + expPart[i] + " part files to " + target.getPath() + ".");
        Frame.export(rebalanced, target.getPath(), "export", false, parts).get();
        // check the number of produced part files (only if the number was given)
        if (expPart[i] != -1) {
          assertEquals(expPart[i], folder.listFiles().length);
          if (parts == 1) {
            assertTrue(target.exists());
          } else {
            for (int j = 0; j < expPart[i]; j++) {
              String suffix = (j < 10) ? "0000" + j : "000" + j;
              assertTrue(new File(folder, "part-m-" + suffix).exists());
            }
          }
        }
        assertTrue(target.exists());
        imported = parseFolder(folder);
        assertEquals(fr.numRows(), imported.numRows());
        assertTrue(TestUtil.isBitIdentical(fr, imported));
      } finally {
        if (rebalanced != null) rebalanced.delete();
        if (imported != null) imported.delete();
        Scope.exit();
      }
    }
    fr.delete();
  }

  private static Frame rebalance(Frame fr, Key targetKey, int nChunks) {
    RebalanceDataSet rb = new RebalanceDataSet(fr, targetKey, nChunks);
    H2O.submitTask(rb);
    rb.join();
    return DKV.get(targetKey).get();
  }

  private static Frame parseFolder(File folder) {
    assert folder.isDirectory();
    File[] files = folder.listFiles();
    assert files != null;
    Arrays.sort(files);
    ArrayList<Key> keys = new ArrayList<>();
    for( File f : files )
      if( f.isFile() )
        keys.add(NFSFileVec.make(f)._key);
    Key[] res = new Key[keys.size()];
    keys.toArray(res);
    return ParseDataset.parse(Key.make(), res);
  }

}
