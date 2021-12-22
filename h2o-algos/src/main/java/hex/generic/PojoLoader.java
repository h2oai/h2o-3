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
import java.lang.reflect.Method;
import java.net.URI;
import java.util.UUID;

class PojoLoader {

    private static final String POJO_EXT = ".java";

    static GenModel loadPojoFromSourceCode(ByteVec sourceVec, Key<Frame> pojoKey) throws IOException {
        String pojoCode = IOUtils.toString(sourceVec.openStream());
        try {
            String path = URI.create(pojoKey.toString()).getPath();
            String fileName = new File(path).getName();
            if (fileName.endsWith(POJO_EXT)) {
                fileName = fileName.substring(0, fileName.length() - POJO_EXT.length());
            }
            return compileAndInstantiate(fileName, pojoCode);
        } catch (Exception e) {
            boolean canCompile = JCodeGen.canCompile();
            boolean selfCheck = compilationSelfCheck();
            throw new IllegalArgumentException(String.format(
                    "POJO compilation failed: " +
                            "Please make sure key '%s' contains a valid POJO source code and you are running a Java JDK " +
                            "(compiler present: '%s', self-check passed: '%s').",
                    pojoKey, canCompile, selfCheck), e);
        }
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
