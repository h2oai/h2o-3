package water.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Arrays;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

import static org.junit.Assert.*;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class FileUtilsTest {

    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testFileDelete() throws Exception {
        // Absolute path
        String absPath = new File(System.getProperty("user.dir")).getAbsolutePath();
        
        // Set up directory with a file + subdirectory with a file
        File mainDir = new File(absPath + "/tmp_h2o_fileutil_delete_test");
        File fileMainDir = new File(mainDir.getAbsolutePath(), "test_main.txt");
        File subDir = new File(mainDir.getAbsolutePath(), "sub_dir");
        File subDirFile = new File(subDir.getAbsolutePath(), "test_sub.txt");
        
        // Ensure directories were just created (should not already exist) and can delete all contents of main directory
        boolean mainDirCreated = mainDir.mkdirs();
        boolean mainDirFileCreated = fileMainDir.mkdirs();
        boolean subDirCreated = subDir.mkdirs();
        boolean subDirFileCreated = subDirFile.mkdirs();
        if (mainDirCreated && mainDirFileCreated && subDirCreated && subDirFileCreated) {
            FileUtils.delete(mainDir);
            boolean fileExists = mainDir.exists();
            assertFalse(fileExists);
        } else {
            throw new Exception("Path, " + mainDir + ", was not cleaned up in previous run. The following file(s) & " +
                    "sub-directories were found: " + Arrays.toString(mainDir.list()));
        }
    }
    
    @Test
    public void testFindFileInPredefinedPath() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        final Method testedMethod = FileUtils.class.getDeclaredMethod("findFileInPredefinedPath",
                String.class);
        assertTrue(Modifier.isStatic(testedMethod.getModifiers()));
        assertTrue(Modifier.isPrivate(testedMethod.getModifiers()));
        try {
            final File h2oHomeDir = new File(System.getProperty("user.dir")).getParentFile();
            environmentVariables.set("H2O_FILES_SEARCH_PATH", h2oHomeDir.getAbsolutePath());
            testedMethod.setAccessible(true);
            testFileFound("smalldata/testng/airlines_train.csv", testedMethod);
            testFileFound("./smalldata/testng/airlines_train.csv", testedMethod);
            testFileFound("../smalldata/testng/airlines_train.csv", testedMethod);
            testFileFound("../../../smalldata/testng/airlines_train.csv", testedMethod);
            testFileNotFound("some/non/existing/path", testedMethod);
            // Existing file, "/" at the beginning marks absolute path - should not find.
            testFileNotFound("/smalldata/testng/airlines_train.csv", testedMethod);

            // Test with "/" at the end of the path to test the file path and the directory path are joined correctly
            environmentVariables.set("H2O_FILES_SEARCH_PATH", h2oHomeDir.getAbsolutePath() + "/");
            assertTrue(System.getenv("H2O_FILES_SEARCH_PATH").endsWith("/"));

            testFileFound("smalldata/testng/airlines_train.csv", testedMethod);
            testFileFound("./smalldata/testng/airlines_train.csv", testedMethod);
            testFileFound("../smalldata/testng/airlines_train.csv", testedMethod);
            testFileFound("../../../smalldata/testng/airlines_train.csv", testedMethod);
            testFileNotFound("/smalldata/testng/airlines_train.csv", testedMethod);

        } finally {
            testedMethod.setAccessible(false);
        }

    }

    private static void testFileFound(final String filePath, final Method findFileInPredefinedPath) throws InvocationTargetException,
            IllegalAccessException {
        final Object returnedObject = findFileInPredefinedPath.invoke(null, filePath);
        assertEquals(Optional.class, returnedObject.getClass());
        final Optional<File> returnedOptional = (Optional) returnedObject;
        assertTrue(returnedOptional.isPresent());
        assertTrue(returnedOptional.get().exists());
        assertTrue(returnedOptional.get().isFile());
    }

    private static void testFileNotFound(final String filePath, final Method findFileInPredefinedPath) throws InvocationTargetException,
            IllegalAccessException {
        final Object returnedObject = findFileInPredefinedPath.invoke(null, filePath);
        assertEquals(Optional.class, returnedObject.getClass());
        final Optional<File> returnedOptional = (Optional) returnedObject;
        assertFalse(returnedOptional.isPresent());
    }

    @Test
    public void testFileImportWithPredefinedPath() {
        try {
            Scope.enter();
            final File h2oHomeDir = new File(System.getProperty("user.dir")).getParentFile();
            environmentVariables.set("H2O_FILES_SEARCH_PATH", h2oHomeDir.getAbsolutePath());

            final Frame trainingFrame = Scope.track(parseTestFile("./smalldata/testng/airlines_train.csv"));
            assertNotNull(trainingFrame);
        } finally {
            Scope.exit();
        }
    }
}
