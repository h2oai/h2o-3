package water.fvec;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.TestUtil;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class ParallelExportTest extends TestUtil {

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Test
    public void testParallelExport() throws IOException {
        Frame fr = parse_test_file("smalldata/testng/airlines.csv");
        assertTrue(fr.anyVec().nChunks() > 1);
        try {
            File targetSingle = new File(tmpFolder.getRoot(), "export_single.csv");
            Frame.export(fr, targetSingle.getAbsolutePath(), fr._key.toString(),
                    false, 1, false, null, new Frame.CSVStreamParams()).get();

            File targetParallel = new File(tmpFolder.getRoot(), "export_parallel.csv");
            Frame.export(fr, targetParallel.getAbsolutePath(), fr._key.toString(),
                    false, 1, true, null, new Frame.CSVStreamParams()).get();

            assertTrue(FileUtils.contentEquals(targetSingle, targetParallel));
        } finally {
            fr.delete();
        }
    }
}
