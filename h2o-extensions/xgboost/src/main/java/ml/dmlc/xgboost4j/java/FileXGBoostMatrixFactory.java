package ml.dmlc.xgboost4j.java;

import water.DKV;
import water.H2O;
import water.Value;
import water.fvec.ByteVec;
import water.fvec.Frame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class FileXGBoostMatrixFactory extends XGBoostMatrixFactory {
    
    private final String matrixDir;

    public FileXGBoostMatrixFactory(
        String matrixDir
    ) {
        this.matrixDir = matrixDir;
    }

    @Override
    public DMatrix makeLocalMatrix() throws IOException, XGBoostError {
        String location = matrixDir;
        if (!location.endsWith("/")) location = location + "/";
        location = location + "matrix.part" + H2O.SELF.index();
        ArrayList<String> keys = new ArrayList<>();
        H2O.getPM().importFiles(location, "", new ArrayList<>(), keys, new ArrayList<>(), new ArrayList<>());
        Value value = DKV.get(keys.get(0));
        ByteVec bv = (ByteVec) ((Frame)value.get()).vecs()[0];
        File tempFile = null;
        try (InputStream is = bv.openStream(null)) {
            tempFile = File.createTempFile("xgb", ".dmatrix");
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                int bytes;
                byte[] buff = new byte[1024];
                while ((bytes = is.read(buff)) > 0) {
                    out.write(buff, 0, bytes);
                }
            }
            return new DMatrix(tempFile.getAbsolutePath());
        } finally {
            if (tempFile != null) tempFile.delete();
        }
    }

}
