package water.util;

import hex.Model;
import org.apache.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.H2O;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class LogTest {

    private static final int ONE_MEGABYTE_IN_BYTES = 1048576;
    private static final String LOGGED_MESSAGE = "In Flanders fields the poppies blow, Between the crosses, row on row.";
    private static final String CONTENT_STRING = "08-23 17:29:21.219 192.168.0.102:54321   47986        main  INFO water.default: In Flanders fields the poppies blow, Between the crosses, row on row.\n";
    private static final String INFO_LOG_MESSAGE = "Info message..";
    private static final String WARN_LOG_MESSAGE = "Warn message!";
    private static final String FATAL_LOG_MESSAGE = "Fatal message!!!";
    
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
                previousSize = infoLoggingFile.length();
                Log.info(LOGGED_MESSAGE);
            }
            final File archivedInfoLoggingFile = new File(Log.getLogFilePath("info") + ".1");
            assertTrue(archivedInfoLoggingFile.length() <= ONE_MEGABYTE_IN_BYTES + CONTENT_STRING.getBytes().length);
            assertTrue(infoLoggingFile.length() <= CONTENT_STRING.getBytes().length);
        } finally {
            H2O.ARGS.log_dir = originalLogDir;
            Log.init(H2O.ARGS.log_level, H2O.ARGS.quiet, H2O.ARGS.max_log_file_size);
        }
    }

    @Test
    public void testPackageSpecificLevelConstraintsConfiguration() throws IOException {
        final String originalLogDir = H2O.ARGS.log_dir;
        try {
            final File temporaryLogDirectory = temporaryFolder.newFolder();
            H2O.ARGS.log_dir = temporaryLogDirectory.getAbsolutePath();
            Log.init(null, true, "1MB");
            Log.info("Log entry to trigger logging system initialisation.");
            
            Logger logger = Logger.getLogger(org.apache.http.auth.AuthOption.class);
            logger.info(INFO_LOG_MESSAGE);
            assertFalse(checkThatMessageIsInLogfile("org.apache.http.auth.AuthOption: " + INFO_LOG_MESSAGE,  new File(Log.getLogFilePath("info"))));
            logger.fatal(FATAL_LOG_MESSAGE);
            assertTrue(checkThatMessageIsInLogfile("org.apache.http.auth.AuthOption: " + FATAL_LOG_MESSAGE,  new File(Log.getLogFilePath("debug"))));
            logger.warn(WARN_LOG_MESSAGE);
            assertTrue(checkThatMessageIsInLogfile("org.apache.http.auth.AuthOption: " + WARN_LOG_MESSAGE,  new File(Log.getLogFilePath("debug"))));

            logger = Logger.getLogger(Model.class);
            logger.info(INFO_LOG_MESSAGE);
            assertTrue(checkThatMessageIsInLogfile("Model: " + INFO_LOG_MESSAGE,  new File(Log.getLogFilePath("info"))));

            logger = Logger.getLogger(com.amazonaws.annotation.Beta.class);
            logger.info(INFO_LOG_MESSAGE);
            assertFalse(checkThatMessageIsInLogfile("com.amazonaws.annotation.Beta: " + INFO_LOG_MESSAGE,  new File(Log.getLogFilePath("info"))));
            logger.warn(WARN_LOG_MESSAGE);
            assertTrue(checkThatMessageIsInLogfile("com.amazonaws.annotation.Beta: " + WARN_LOG_MESSAGE,  new File(Log.getLogFilePath("debug"))));
            logger.fatal(FATAL_LOG_MESSAGE);
            assertTrue(checkThatMessageIsInLogfile("com.amazonaws.annotation.Beta: " + FATAL_LOG_MESSAGE,  new File(Log.getLogFilePath("debug"))));
            
        } finally {
            H2O.ARGS.log_dir = originalLogDir;
            Log.init(H2O.ARGS.log_level, H2O.ARGS.quiet, H2O.ARGS.max_log_file_size);
        }
    }
    
    boolean checkThatMessageIsInLogfile(String message, File file) {
            return lastLine(file).contains(message);
    }

    public String lastLine(File file) {
        RandomAccessFile fileHandler = null;
        try {
            fileHandler = new RandomAccessFile( file, "r" );
            long fileLength = fileHandler.length() - 1;
            StringBuilder sb = new StringBuilder();

            for (long filePointer = fileLength; filePointer != -1; filePointer--) {
                fileHandler.seek(filePointer);
                int readByte = fileHandler.readByte();

                if (readByte == 0xA) {
                    if (filePointer == fileLength) {
                        continue;
                    }
                    break;
                } else if (readByte == 0xD) {
                    if (filePointer == fileLength - 1) {
                        continue;
                    }
                    break;
                }

                sb.append((char) readByte);
            }

            String lastLine = sb.reverse().toString();
            return lastLine;
        } catch (java.io.FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (java.io.IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (fileHandler != null)
                try {
                    fileHandler.close();
                } catch (IOException e) {
                    /* ignore */
                }
        }
    }

}
