package water.persist;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.*;
import water.exceptions.H2OFileAccessDeniedException;
import water.fvec.*;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;

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
    public void createReturnsBufferedOutputStreamForFiles() throws IOException {
        File target = new File(tmp.getRoot(), "target.txt");
        try (OutputStream os = persistManager.create(target.getAbsolutePath(), false)) {
            assertTrue(os instanceof BufferedOutputStream);
        }
    }

    @Test
    public void deltaLogFilesAreFiltered() throws IOException {
        testDeltaLogFilesAreFiltered(File::getAbsolutePath);
    }

    @Test
    public void deltaLogFilesAreFiltered_pubdev() throws IOException {
        testDeltaLogFilesAreFiltered(dir -> dir.getAbsolutePath() + "/");
    }

    private void testDeltaLogFilesAreFiltered(Function<File, String> makePath) throws IOException {
        File dir = tmp.newFolder("to_import");
        Files.write(new File(dir, "file-a").toPath(), Collections.singleton("file-a"));
        Files.write(new File(dir, "file-b").toPath(), Collections.singleton("file-b"));
        File deltaLogDir = new File(dir, "_delta_log");
        assertTrue(deltaLogDir.mkdir());
        Files.write(new File(deltaLogDir, "000.json").toPath(), Collections.singleton("json data"));
        Files.write(new File(deltaLogDir, "000.crc").toPath(), Collections.singleton("crc data"));

        ArrayList<String> files = new ArrayList<>();
        ArrayList<String> keys = new ArrayList<>();
        ArrayList<String> fails = new ArrayList<>();
        ArrayList<String> dels = new ArrayList<>();
        try {
            persistManager.importFiles(makePath.apply(dir), null, files, keys, fails, dels);

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

    @Test
    public void testIsHexPath() {
        assertTrue(persistManager.isHexPath("hex://anything"));
        assertFalse(persistManager.isHexPath("http://anything"));
    }

    @Test
    public void testToHexPath() {
        Scope.enter();
        try {
            Frame f = TestFrameCatalog.oneChunkFewRows();
            Key chunkKey = f.anyVec().chunkKey(0);
            String hexPath = persistManager.toHexPath(chunkKey);
            assertTrue(hexPath.startsWith("hex://"));
            assertTrue(persistManager.isHexPath(hexPath));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testOpenHexPath() throws IOException {
        Scope.enter();
        try {
            Vec v = Vec.makeConN(10, 1);
            Scope.track(v);
            byte[] data = new byte[(int) v.length()];
            Key chunkKey = v.chunkKey(0);
            DKV.put(chunkKey, new C1NChunk(data));

            InputStream is = persistManager.open(persistManager.toHexPath(chunkKey));
            assertTrue(is instanceof ByteArrayInputStream);
            assertEquals(data.length, is.available());
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testCreateHexPath() throws IOException {
        Scope.enter();
        try {
            Vec v = Vec.makeConN(10, 1);
            Key chunkKey = v.chunkKey(0);
            Scope.track(v);

            byte[] newData = new byte[v.chunkLen(0)];
            for (int i = 0; i < newData.length; i++)
                newData[i] = (byte) i;
            try (OutputStream os = persistManager.create(persistManager.toHexPath(chunkKey), true)) {
                os.write(newData);
            }
            Chunk c = v.chunkForChunkIdx(0);
            assertTrue(c instanceof C1NChunk);
            assertArrayEquals(newData, c.asBytes());
        } finally {
            Scope.exit();
        }
    }


    @Test
    public void testDeltaLakeMetadataFilter() {
        PersistManager.DeltaLakeMetadataFilter filter = new PersistManager.DeltaLakeMetadataFilter();
        List<String> result = filter.apply(null, new ArrayList<>(Arrays.asList(
                "hdfs://localhost",
                "/a/file.parquet",
                "/a/_delta_log/b/_delta_log/00.crc",
                "/a/_delta_log/b/fileA.parquet",
                "dbfs:///_delta_log/00.crc",
                "dbfs:///_delta_log/b/fileB.parquet"
        )));
        assertEquals(Arrays.asList(
                "hdfs://localhost",
                "/a/file.parquet",
                "/a/_delta_log/b/fileA.parquet",
                "dbfs:///_delta_log/b/fileB.parquet"
        ), result);
    }

    @Test
    public void testImportFileMatchingDenyList() {
        Exception actual = null;
        try {
            persistManager.importFiles("/etc/hosts", null, new ArrayList<String>(), new ArrayList<String>(), new ArrayList<String>(), new ArrayList<String>());
        } catch (Exception e) {
            actual = e;
        }
        assertNotNull(actual);
        assertTrue(actual instanceof H2OFileAccessDeniedException);
    }

}
