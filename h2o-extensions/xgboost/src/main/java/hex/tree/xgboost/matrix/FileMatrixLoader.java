package hex.tree.xgboost.matrix;

import org.apache.log4j.Logger;
import water.Key;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import static hex.tree.xgboost.remote.RemoteXGBoostUploadServlet.getMatrixFile;

public class FileMatrixLoader extends MatrixLoader {

    private static final Logger LOG = Logger.getLogger(FileMatrixLoader.class);

    private final Key modelKey;

    public FileMatrixLoader(Key modelKey) {
        this.modelKey = modelKey;
    }

    private DMatrixProvider loadProvider(File file) {
        LOG.info("Loading matrix data from " + file + " of size " + file.length() + ", " + (file.length() / (1000d * 1000 * 1000)) + "G");
        try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(file))) {
            return (DMatrixProvider) is.readObject();
        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException("Failed to read matrix data into memory", e);
        } finally {
            file.delete();
        }
    }
    
    @Override
    public DMatrixProvider makeLocalMatrix() {
        final File matrixFile = getMatrixFile(modelKey.toString());
        LOG.debug("Loading matrix data from " + matrixFile);
        return loadProvider(matrixFile);
    }

}
