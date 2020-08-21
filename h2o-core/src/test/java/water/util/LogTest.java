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

    private static final int ONE_MEGABYTE_IN_BYTES = 1048576;
    private static final String LOGGED_MESSAGE = "In Flanders fields the poppies blow, Between the crosses, row on row.";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testLogFileRotation() throws Exception {
        final String originalLogDir = H2O.ARGS.log_dir;
        try {
            final File temporaryLogDirectory = temporaryFolder.newFolder();
            H2O.ARGS.log_dir = temporaryLogDirectory.getAbsolutePath();
            Log.init(null, true, "1MB");
            Log.info("Log entry to trigger logging system initialisation.");

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
            Log.init(H2O.ARGS.log_level, H2O.ARGS.quiet, H2O.ARGS.max_log_file_size);
        }
    }

}
