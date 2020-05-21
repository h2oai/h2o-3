package hex.tree.xgboost.matrix;

import hex.tree.xgboost.task.XGBoostSetupTask;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

public class FileMatrixLoader extends MatrixLoader {

    private static final Logger LOG = Logger.getLogger(FileMatrixLoader.class);

    private final String matrixDirPath;
    
    public FileMatrixLoader(String matrixDirPath) {
        this.matrixDirPath = matrixDirPath;
    }

    private DMatrixProvider loadProvider(File file) {
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
        final File matrixFile = XGBoostSetupTask.getMatrixFile(new File(matrixDirPath));
        LOG.debug("Loading matrix data from " + matrixFile);
        return loadProvider(matrixFile);
    }

}
