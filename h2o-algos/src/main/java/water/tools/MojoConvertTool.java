package water.tools;

import hex.generic.Generic;
import hex.generic.GenericModel;
import water.ExtensionManager;
import water.H2O;
import water.Paxos;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Convenience command line tool for converting H2O MOJO to POJO
 */
public class MojoConvertTool {

    private final File _mojo_file;
    private final File _pojo_file;

    public MojoConvertTool(File mojoFile, File pojoFile) {
        _mojo_file = mojoFile;
        _pojo_file = pojoFile;
    }

    void convert() throws IOException {
        GenericModel mojo = Generic.importMojoModel(_mojo_file.getAbsolutePath(), true);
        String pojo = mojo.toJava(false, true);
        Path pojoPath = Paths.get(_pojo_file.toURI());
        Files.write(pojoPath, pojo.getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws IOException {
        try {
            mainInternal(args);
        }
        catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public static void mainInternal(String[] args) throws IOException {
        if (args.length < 2 || args[0] == null || args[1] == null) {
            throw new IllegalArgumentException("java -cp h2o.jar " + MojoConvertTool.class.getName() + " source_mojo.zip target_pojo.java");
        }

        File mojoFile = new File(args[0]);
        if (!mojoFile.exists() || !mojoFile.isFile()) {
            throw new IllegalArgumentException("Specified MOJO file (" + mojoFile.getAbsolutePath() + ") doesn't exist!");
        }
        File pojoFile = new File(args[1]);
        if (pojoFile.isDirectory() || (pojoFile.getParentFile() != null && !pojoFile.getParentFile().isDirectory())) {
            throw new IllegalArgumentException("Invalid target POJO file (" + pojoFile.getAbsolutePath() + ")! Please specify a file in an existing directory.");
        }

        System.out.println();
        System.out.println("Starting local H2O instance to facilitate MOJO to POJO conversion.");
        System.out.println();

        H2O.main(new String[]{"-disable_web", "-ip", "localhost", "-disable_net"});
        ExtensionManager.getInstance().registerRestApiExtensions();

        H2O.waitForCloudSize(1, 60_000);
        Paxos.lockCloud("H2O is started in a single node configuration.");

        System.out.println();
        System.out.println("Converting " + mojoFile + " to " + pojoFile + "...");
        new MojoConvertTool(mojoFile, pojoFile).convert();
        System.out.println("DONE");
    }

}
