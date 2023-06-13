package water.persist;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

import java.io.IOException;
import java.net.URL;

public abstract class DriveClientFactory {

    /**
     * Creates a new instance of a DriveClient backed by a Python backend
     * @param venvExePath path to Python executable (presumably located in a virtual environment)
     * @return instance of DriveClient
     * @throws IOException if Python client script cannot be found
     */
    public static DriveClient createDriveClient(String venvExePath) throws IOException {
        Context context = Context.newBuilder("python").
                allowIO(true).
                allowAllAccess(true).
                option("python.Executable", venvExePath).
                build();
        URL scriptURL = DriveClientFactory.class.getResource("/drive_client.py");
        if (scriptURL == null) {
            throw new IllegalStateException("Drive client (drive_client.py) not bundled in the jar.");
        }
        Source scriptSource = Source.newBuilder("python", scriptURL)
                .build();
        context.eval("python", "import site");
        context.eval(scriptSource);
        DriveClientDelegate delegate = context.getBindings("python")
                .getMember("DriveClient")
                .as(DriveClientDelegate.class);
        return new DriveClient(delegate);
    }

}
