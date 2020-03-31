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

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class FileUtilsTest {

    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testFindFileInPredefinedPath() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        final Method findFileInPredefinedPath = FileUtils.class.getDeclaredMethod("findFileInPredefinedPath",
                String.class);
        assertTrue(Modifier.isStatic(findFileInPredefinedPath.getModifiers()));
        assertTrue(Modifier.isPrivate(findFileInPredefinedPath.getModifiers()));
        try {
            final File h2oHomeDir = new File(System.getProperty("user.dir")).getParentFile();
            environmentVariables.set("H2O_FILES_SEARCH_PATH", h2oHomeDir.getAbsolutePath());

            findFileInPredefinedPath.setAccessible(true);
            final Object returnedObject = findFileInPredefinedPath.invoke(null,
                    "./smalldata/testng/airlines_train.csv");
            assertEquals(Optional.class, returnedObject.getClass());
            final Optional<File> returnedOptional = (Optional) returnedObject;
            assertTrue(returnedOptional.isPresent());
            assertTrue(returnedOptional.get().exists());
            assertTrue(returnedOptional.get().isFile());
        } finally {
            findFileInPredefinedPath.setAccessible(false);
        }

    }

    @Test
    public void testFileImportWithPredefinedPath() {
        try {
            Scope.enter();
            final File h2oHomeDir = new File(System.getProperty("user.dir")).getParentFile();
            environmentVariables.set("H2O_FILES_SEARCH_PATH", h2oHomeDir.getAbsolutePath());

            final Frame trainingFrame = Scope.track(TestUtil.parse_test_file("./smalldata/testng/airlines_train.csv"));
            assertNotNull(trainingFrame);
        } finally {
            Scope.exit();
        }
    }
}
