package hex.genmodel;

import hex.genmodel.tools.PrintMojo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Permission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PrintMojoTest {

    private Path gbmMojoFile;
    private Path targetFile;
    private String outputPngFilename;
    private SecurityManager originalSecurityManager;

    @Before
    public void setup() throws IOException {
        gbmMojoFile = copyMojoFileResource("test_h2o.csv.zip");
        targetFile = Files.createTempFile("", "test_h2o.csv.gv");
        outputPngFilename = "exampleh2o.png";
        originalSecurityManager = System.getSecurityManager();
        System.setSecurityManager(new PreventExitSecurityManager());
    }

    @After
    public void tearDown() throws Exception {
        System.setSecurityManager(originalSecurityManager);
        Files.deleteIfExists(gbmMojoFile);
        Files.deleteIfExists(targetFile);
    }

    @Test
    public void testPrintMojoNone() throws IOException {
        String[] none = {
                "--tree", "0",
                "-i", gbmMojoFile.toAbsolutePath().toString(),
                "-f", "20", "-d", "3"
        };
        try {
            PrintMojo.main(none);
        } catch (PreventedExitException e) {
            assertEquals(0, e.status); // PrintMojo is expected to finish without errors
        }
        Path pathToImage = Paths.get(outputPngFilename);
        Assert.assertFalse(Files.deleteIfExists(pathToImage));
    }

    @Test
    public void testPrintMojoDirectOnly() throws IOException {
        String[] directOnly = {
                "--tree", "0",
                "-i", gbmMojoFile.toAbsolutePath().toString(),
                "-f", "20", "-d", "3", "--direct", outputPngFilename
        };
        try {
            PrintMojo.main(directOnly);
            fail("Expected PrintMojo to exit");
        } catch (PreventedExitException e) {
            assertEquals(0, e.status); // PrintMojo is expected to finish without errors
        }
        Path pathToImage = Paths.get(outputPngFilename);
        Assert.assertTrue(Files.deleteIfExists(pathToImage));
    }

    @Test
    public void testPrintMojoBoth() throws IOException {
        String[] both = {
                "--tree", "0",
                "-i", gbmMojoFile.toAbsolutePath().toString(),
                "-o", targetFile.toAbsolutePath().toString(),
                "-f", "20", "-d", "3", "--direct", outputPngFilename
        };
        try {
            PrintMojo.main(both);
            fail("Expected PrintMojo to exit");
        } catch (PreventedExitException e) {
            assertEquals(0, e.status); // PrintMojo is expected to finish without errors
        }

        Path pathToImage = Paths.get(outputPngFilename);
        Assert.assertTrue(Files.deleteIfExists(pathToImage));
    }

    @Test
    public void testPrintMojoOutputOnly() throws IOException {
        String[] outputOnly = {
                "--tree", "0",
                "-i", gbmMojoFile.toAbsolutePath().toString(),
                "-o", targetFile.toAbsolutePath().toString(),
                "-f", "20", "-d", "3"
        };
        try {
            PrintMojo.main(outputOnly);
        } catch (PreventedExitException e) {
            assertEquals(0, e.status); // PrintMojo is expected to finish without errors
        }
        Path pathToImage = Paths.get(outputPngFilename);
        Assert.assertFalse(Files.deleteIfExists(pathToImage));
    }


    private Path copyMojoFileResource(String name) throws IOException {
        Path target = Files.createTempFile("", name);
        try (InputStream is = getClass().getResourceAsStream(name)) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }


    protected static class PreventedExitException extends SecurityException {
        public final int status;

        public PreventedExitException(int status) {
            this.status = status;
        }
    }

    /**
     * Security managers that prevents PrintMojo from exiting.
     */
    private static class PreventExitSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission perm) {
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
        }

        @Override
        public void checkExit(int status) {
            throw new PreventedExitException(status);
        }
    }
}
