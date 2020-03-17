package ml.dmlc.xgboost4j.java;

import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostModel;
import water.DKV;
import water.H2O;
import water.Value;
import water.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public class ExternalXGBoostSetupTask extends XGBoostSetupTask {
    
    private final String matrixDir;

    public ExternalXGBoostSetupTask(
        XGBoostModel model,
        XGBoostModel.XGBoostParameters parms,
        BoosterParms boosterParms,
        byte[] checkpointToResume,
        Map<String, String> rabitEnv,
        FrameNodes trainFrame,
        String matrixDir
    ) {
        super(model, parms, boosterParms, checkpointToResume, rabitEnv, trainFrame);
        this.matrixDir = matrixDir;
    }

    @Override
    protected DMatrix makeLocalMatrix() throws IOException, XGBoostError {
        String location = matrixDir;
        if (!location.endsWith("/")) location = location + "/";
        location = location + "matrix.part" + H2O.SELF.index();
        ArrayList<String> keys = new ArrayList<>();
        H2O.getPM().importFiles(location, "", new ArrayList<>(), keys, new ArrayList<>(), new ArrayList<>());
        Value value = DKV.get(keys.get(0));
        byte[] matrixData = H2O.getPM().getPersistForURI(FileUtils.getURI(location)).load(value);
        File tempFile = null;
        try {
            tempFile = File.createTempFile("xgb", ".dmatrix");
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                out.write(matrixData);
            }
            return new DMatrix(tempFile.getAbsolutePath());
        } finally {
            if (tempFile != null) tempFile.delete();
        }
    }

}
