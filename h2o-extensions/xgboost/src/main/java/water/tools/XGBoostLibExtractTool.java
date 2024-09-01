package water.tools;

import hex.tree.xgboost.XGBoostExtension;
import hex.tree.xgboost.util.NativeLibrary;
import hex.tree.xgboost.util.NativeLibraryLoaderChain;

import java.io.File;
import java.io.IOException;

public class XGBoostLibExtractTool {

    public static void main(String[] args) throws IOException {
        try {
            mainInternal(args);
        } catch (IllegalArgumentException e) {
            System.err.println((e.getMessage()));
            System.exit(1);
        }
    }

    public static void mainInternal(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("XGBoostLibExtractTool: Specify target directory where to extract XGBoost native libraries.");
        }
        File dir = new File(args[0]);
        if (!dir.exists()) {
            throw new IllegalArgumentException("XGBoostLibExtractTool: Directory '" + dir.getAbsolutePath() + "' doesn't exist.");
        }
        NativeLibraryLoaderChain loader = XGBoostExtension.getLoader();
        if (loader == null) {
            throw new IllegalArgumentException("XGBoostLibExtractTool: Failed to locate native libraries.");
        }
        for (NativeLibrary lib : loader.getNativeLibs()) {
            if (!lib.isBundled())
                continue;
            File libFile = lib.extractTo(dir);
            System.out.println("Extracted native library: " + libFile.getAbsolutePath());
        }
    }

}
