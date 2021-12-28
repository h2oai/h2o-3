package water.network.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

public class ExternalKeytool {

    public static void main(String[] args) throws Exception {
        String javaHome = System.getProperty("java.home");
        String keytoolPath = javaHome != null ?
                new File(javaHome, new File("bin", "keytool").getPath()).getAbsolutePath() : "keytool";
        List<String> command = new ArrayList<>(args.length + 1);
        command.add(keytoolPath);
        command.addAll(Arrays.asList(args));
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(command);
        Process process = builder.start();
        StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream());
        Executors.newSingleThreadExecutor().submit(streamGobbler);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("External keytool execution failed (exit code: " + exitCode + ").");
        }
    }

}
