package hex.tree.xgboost.task;

import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import hex.tree.xgboost.matrix.MatrixLoader;
import org.apache.log4j.Logger;
import water.H2O;
import water.Key;

import java.io.File;
import java.io.IOException;

public class XGBoostSaveMatrixTask extends AbstractXGBoostTask<XGBoostSaveMatrixTask> {

    private static final Logger LOG = Logger.getLogger(XGBoostSaveMatrixTask.class);

    private final MatrixLoader _matrixLoader;
    private final String _saveMatrixDirectory;
    
    protected transient DMatrix matrix;
    
    public XGBoostSaveMatrixTask(Key modelKey, String saveMatrixDirectory, boolean[] hasDMatrix, MatrixLoader loader) {
        super(modelKey, hasDMatrix);
        _saveMatrixDirectory = saveMatrixDirectory;
        _matrixLoader = loader;
    }

    @Override
    protected void execute() {
        try {
            matrix = _matrixLoader.makeLocalMatrix();
        } catch (XGBoostError | IOException xgBoostError) {
            throw new IllegalStateException("Failed to create XGBoost DMatrix", xgBoostError);
        }
        if (_saveMatrixDirectory != null) {
            File directory = new File(_saveMatrixDirectory);
            if (directory.mkdirs()) {
                LOG.debug("Created directory for matrix export: " + directory.getAbsolutePath());
            }
            File path = getMatrixFile(directory);
            LOG.info("Saving node-local portion of XGBoost training dataset to " + path.getAbsolutePath() + ".");
            matrix.saveBinary(path.getAbsolutePath());
        }
    }
    
    public static File getMatrixFile(File dir) {
        return new File(dir, "matrix.part" + H2O.SELF.index());
    }

}
