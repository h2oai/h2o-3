package water.persist;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.*;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.*;

public class PersistManagerTest extends TestUtil {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    PersistManager persistManager;

    @Before
    public void setUp() {
        stall_till_cloudsize(1);
        persistManager = H2O.getPM();
    }

    @Test
    public void calcTypeaheadMatches_emptyPath() {
        // Completely empty path
        List<String> matches = persistManager.calcTypeaheadMatches("", 100);
        assertNotNull(matches);
        assertEquals(0, matches.size());
        
        // Path with spaces (testing trim is being done)
        matches = persistManager.calcTypeaheadMatches("   ", 100);
        assertNotNull(matches);
        assertEquals(0, matches.size());
    }

    @Test
    public void createReturnsBufferedOutputStreamForFiles() throws IOException  {
        File target = new File(tmp.getRoot(), "target.txt");
        try (OutputStream os = persistManager.create(target.getAbsolutePath(), false)) {
            assertTrue(os instanceof BufferedOutputStream);
        }
    }

    @Test
    public void deltaLogFilesAreFiltered() throws IOException {
        File dir = tmp.newFolder("to_import");
        Files.write(new File(dir, "file-a").toPath(), Collections.singleton("file-a"));
        Files.write(new File(dir, "file-b").toPath(), Collections.singleton("file-a"));
        File deltaLogDir = new File(dir, "_delta_log");
        assertTrue(deltaLogDir.mkdir());
        Files.write(new File(deltaLogDir, "000.json").toPath(), Collections.singleton("json data"));
        Files.write(new File(deltaLogDir, "000.crc").toPath(), Collections.singleton("crc data"));

        ArrayList<String> files = new ArrayList<>();
        ArrayList<String> keys = new ArrayList<>();
        ArrayList<String> fails = new ArrayList<>();
        ArrayList<String> dels = new ArrayList<>();
        try {
            persistManager.importFiles(dir.getAbsolutePath(), null, files, keys, fails, dels);

            assertEquals(2, files.size());
            assertEquals(2, keys.size());
            assertEquals(0, fails.size());
            assertEquals(0, dels.size());

            assertEquals(new HashSet<>(Arrays.asList(
                    new File(dir, "file-a").getAbsolutePath(),
                    new File(dir, "file-b").getAbsolutePath()
            )), new HashSet<>(files));
        } finally {
            for (String k : keys) {
                Keyed.remove(Key.make(k));
            }
        }
    }
    
}
