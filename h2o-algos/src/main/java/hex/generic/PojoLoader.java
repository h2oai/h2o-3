package hex.generic;

import hex.genmodel.GenModel;
import org.apache.commons.io.IOUtils;
import water.Key;
import water.fvec.ByteVec;
import water.fvec.Frame;
import water.util.JCodeGen;

import java.io.File;
import java.io.IOException;
import java.net.URI;

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
            Class<?> clz = JCodeGen.compile(fileName, pojoCode);
            return (GenModel) clz.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(
                    "Invalid POJO source code - compilation error. Please make sure key '%s' contains a valid POJO source code. ", pojoKey));
        }
    }

}
