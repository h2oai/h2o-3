package hex.tree.xgboost.task;

import hex.tree.xgboost.matrix.FrameMatrixLoader;
import hex.tree.xgboost.matrix.MatrixLoader;
import org.apache.log4j.Logger;
import water.H2O;
import water.Key;

import java.io.*;

public class XGBoostSaveMatrixTask extends AbstractXGBoostTask<XGBoostSaveMatrixTask> {

    private static final Logger LOG = Logger.getLogger(XGBoostSaveMatrixTask.class);

    private final FrameMatrixLoader _matrixLoader;
    private final String _saveMatrixDirectory;

    protected transient MatrixLoader.DMatrixProvider matrix;

    public XGBoostSaveMatrixTask(Key modelKey, String saveMatrixDirectory, boolean[] hasDMatrix, FrameMatrixLoader loader) {
        super(modelKey, hasDMatrix);
        _saveMatrixDirectory = saveMatrixDirectory;
        _matrixLoader = loader;
    }

    @Override
    protected void execute() {
        File directory = new File(_saveMatrixDirectory);
        if (directory.mkdirs()) {
            LOG.debug("Created directory for saving matrix " + directory.getAbsolutePath());
        }
        File matrixFile = getMatrixFile(new File(_saveMatrixDirectory));
        try (ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(matrixFile))) {
            os.writeObject(_matrixLoader.makeLocalMatrix());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save the matrix to file-system.", e);
        }
    }

    public static File getMatrixFile(File dir) {
        return new File(dir, "matrix.part" + H2O.SELF.index());
    }

}
