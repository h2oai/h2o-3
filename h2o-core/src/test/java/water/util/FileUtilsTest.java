package water.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runner.RunWith;
import water.Scope;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.IOException;
import java.io.File;
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

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    
    @Test
    public void testDeleteDirTraversal() throws Exception {
        SetupDirStructure setupDirStructure = new SetupDirStructure().invoke();
        File mainDir = setupDirStructure.getMainDir();
        File fileMainDir = setupDirStructure.getFileMainDir();
        File subDir = setupDirStructure.getSubDir();
        File subDirFile = setupDirStructure.getSubDirFile();

        assertTrue("Directory, " + mainDir.getAbsolutePath() + " does not exist", mainDir.exists());
        assertTrue("File, " + fileMainDir.getAbsolutePath() + " does not exist", fileMainDir.exists());
        assertTrue("Sub-directory, " + subDir.getAbsolutePath() + " does not exist", subDir.exists());
        assertTrue("File, " + subDirFile.getAbsolutePath() + " does not exist", subDirFile.exists());
        
        // Delete subdir file, but ensure sub dir still exists
        FileUtils.delete(subDirFile);
        assertTrue("Sub-directory, " + subDir.getAbsolutePath() + " , shouldn't have been deleted", subDir.exists());
        assertFalse("Sub-directory file, " + subDirFile.getAbsolutePath() + ", should've been deleted", subDirFile.exists());

        // Delete subdir, but ensure main dir still exists
        FileUtils.delete(subDir);
        assertTrue("Top level directory, " + mainDir.getAbsolutePath() + ", shouldn't have been deleted", mainDir.exists());
        assertFalse("Sub-directory, " + subDir.getAbsolutePath() + " , should've been deleted",subDir.exists());
        
        // Delete file in main dir, but ensure main dir still exists
        FileUtils.delete(fileMainDir);
        assertTrue("Top level directory, " + mainDir.getAbsolutePath() + ", shouldn't have been deleted", mainDir.exists());
        assertFalse("Top level directory file, " + fileMainDir.getAbsolutePath() + " , should've been deleted", fileMainDir.exists());
    }

    @Test
    public void testDeleteEntireDir() throws Exception {
        SetupDirStructure setupDirStructure = new SetupDirStructure().invoke();
        File mainDir = setupDirStructure.getMainDir();
        File fileMainDir = setupDirStructure.getFileMainDir();
        File subDir = setupDirStructure.getSubDir();
        File subDirFile = setupDirStructure.getSubDirFile();
        
        assertTrue("Directory, " + mainDir.getAbsolutePath() + " does not exist", mainDir.exists());
        assertTrue("File, " + fileMainDir.getAbsolutePath() + " does not exist", fileMainDir.exists());
        assertTrue("Sub-directory, " + subDir.getAbsolutePath() + " does not exist", subDir.exists());
        assertTrue("File, " + subDirFile.getAbsolutePath() + " does not exist", subDirFile.exists());


        // Ensure deleting an entire directory also deletes all contents, e.g, sub-dirs, files, etc.
        FileUtils.delete(mainDir);
        assertFalse("Directory, " + mainDir.getAbsolutePath() + " should've been deleted", mainDir.exists());
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

    private class SetupDirStructure {
        private File mainDir;
        private File fileMainDir;
        private File subDir;
        private File subDirFile;

        private File getMainDir() {
            return mainDir;
        }

        private File getFileMainDir() {
            return fileMainDir;
        }

        private File getSubDir() {
            return subDir;
        }

        private File getSubDirFile() {
            return subDirFile;
        }

        private SetupDirStructure invoke() throws IOException {
            // Set up directory with a file + subdirectory with a file
            mainDir = temporaryFolder.newFolder("tmp_h2o_fileutil_delete_test");
            fileMainDir = new File(mainDir, "test_main.txt");
            subDir = new File(mainDir, "sub_dir");
            subDirFile = new File(subDir, "test_sub.txt");

            fileMainDir.createNewFile();
            subDir.mkdir();
            subDirFile.createNewFile();
            
            return this;
        }
    }
}
