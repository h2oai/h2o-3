package water.tools;

import hex.tree.xgboost.XGBoostExtension;
import hex.tree.xgboost.util.NativeLibrary;
import hex.tree.xgboost.util.NativeLibraryLoaderChain;

import java.io.File;
import java.io.IOException;

public class XGBoostLibExtractTool {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("XGBoostLibExtractTool: Specify target directory where to extract XGBoost native libraries.");
            System.exit(-1);
        }
        File dir = new File(args[0]);
        if (!dir.exists()) {
            System.err.println("XGBoostLibExtractTool: Directory '" + dir.getAbsolutePath() + "' doesn't exist.");
            System.exit(-1);
        }
        NativeLibraryLoaderChain loader = XGBoostExtension.getLoader();
        if (loader == null) {
            System.err.println("XGBoostLibExtractTool: Failed to locate native libraries.");
            System.exit(-1);
        }
        for (NativeLibrary lib : loader.getNativeLibs()) {
            if (!lib.isBundled())
                continue;
            File libFile = lib.extractTo(dir);
            System.out.println("Extracted native library: " + libFile.getAbsolutePath());
        }
    }

}
