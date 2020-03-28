package water.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.H2O;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.File;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class LogTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private static final int ONE_MEGABYTE_IN_BYTES = 1048576;
    private static final String LOGGED_MESSAGE = "In Flanders fields the poppies blow, Between the crosses, row on row.";

    @Test
    public void testLogFileRotation() throws Exception {
        final String originalLogDir = H2O.ARGS.log_dir;
        try {
            final File temporaryLogDirectory = temporaryFolder.newFolder();
            Log.LOG_DIR = temporaryLogDirectory.getAbsolutePath();
            H2O.ARGS.log_dir = Log.LOG_DIR;
            Log.init(null, true, "1MB");

            assertEquals(temporaryLogDirectory.getAbsolutePath(), Log.getLogDir());
            final File infoLoggingFile = new File(Log.getLogFilePath("info"));
            
            long previousSize = -1;
            while (infoLoggingFile.length() > previousSize) {
                assertTrue(infoLoggingFile.length() < ONE_MEGABYTE_IN_BYTES);
                previousSize = infoLoggingFile.length();
                Log.info(LOGGED_MESSAGE);
            }
            assertEquals(0, infoLoggingFile.length()); // Freshly rotated file
        } finally {
            H2O.ARGS.log_dir = originalLogDir;
            Log.LOG_DIR = originalLogDir;
            Log.init(H2O.ARGS.log_level, H2O.ARGS.quiet, H2O.ARGS.max_log_file_size);
        }
    }

}
