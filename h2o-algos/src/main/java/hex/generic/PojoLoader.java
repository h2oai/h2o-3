package hex.generic;

import hex.genmodel.GenModel;
import org.apache.commons.io.IOUtils;
import water.Key;
import water.fvec.ByteVec;
import water.fvec.Frame;
import water.util.JCodeGen;
import water.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.UUID;

class PojoLoader {

    private static final String POJO_EXT = ".java";

    static GenModel loadPojoFromSourceCode(ByteVec sourceVec, Key<Frame> pojoKey, String modelId) throws IOException {
        final String pojoCode;
        try (InputStream is = sourceVec.openStream()) {
            pojoCode = IOUtils.toString(is, Charset.defaultCharset());
        }
        String className = null;
        try {
            className = inferClassName(pojoKey);
        } catch (Exception e) {
            Log.warn("Exception while trying to automatically infer POJO class name", e);
        }
        if (className == null) {
            Log.warn("Unable automatically infer POJO class name, model_id = `" + modelId + "` will be used instead. " + 
                    "If you encounter further errors make sure you set model_id to the correct class name in import/upload call.");
            className = modelId;
        }
        try {
            return compileAndInstantiate(className, pojoCode);
        } catch (Exception e) {
            boolean canCompile = JCodeGen.canCompile();
            boolean selfCheck = compilationSelfCheck();
            throw new IllegalArgumentException(String.format(
                    "POJO compilation failed: " +
                            "Please make sure key '%s' contains a valid POJO source code for class '%s' and you are running a Java JDK " +
                            "(compiler present: '%s', self-check passed: '%s').",
                    pojoKey, className, canCompile, selfCheck), e);
        }
    }

    static String inferClassName(Key<Frame> pojoKey) {
        String path = URI.create(pojoKey.toString()).getPath();
        String fileName = new File(path).getName();
        if (fileName.endsWith(POJO_EXT)) {
            return fileName.substring(0, fileName.length() - POJO_EXT.length());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static <T> T compileAndInstantiate(String className, String src) throws Exception {
        Class<?> clz = JCodeGen.compile(className, src, false);
        return (T) clz.newInstance();
    }

    static boolean compilationSelfCheck() {
        final String cls = "SelfCheck_" + UUID.randomUUID().toString().replaceAll("-","_");
        final String src = "public class " + cls + " { public double score0() { return Math.E; } }";
        try {
            Object o = compileAndInstantiate(cls, src);
            Method m = o.getClass().getMethod("score0");
            Object result = m.invoke(o);
            return result instanceof Double && (Double) result == Math.E;
        } catch (Exception e) {
            Log.err("Compilation self-check failed", e);
            return false;
        }
    }

}
