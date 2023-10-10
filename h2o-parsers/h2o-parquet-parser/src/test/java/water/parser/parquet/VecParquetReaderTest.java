package water.parser.parquet;

import org.apache.hadoop.fs.FSDataInputStream;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.H2O;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.FileVec;
import water.fvec.Frame;
import water.persist.VecDataInputStream;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class VecParquetReaderTest extends TestUtil {

    @Test
    public void testFooterChunkIsNotCached() throws IOException {
        try {
            Scope.enter();
            FileVec fv = makeNfsFileVec("./smalldata/parser/parquet/airlines-simple.snappy.parquet");
            assertNotNull(fv);
            Scope.track(fv);

            VecDataInputStream vdis = new VecDataInputStream(fv, true);
            FSDataInputStream fsdis = new FSDataInputStream(vdis);

            byte[] footerBytes = VecParquetReader.readFooterAsBytes(fv.length(), fsdis);
            assertNotNull(VecParquetReader.readFooter(footerBytes));

            // now check that we didn't cache anything on a node that doesn't own the chunks
            for (int cidx = 0; cidx < fv.nChunks(); cidx++) {
                Key<?> ck = fv.chunkKey(cidx);
                if (!ck.home()) {
                    assertNull("Chunk #" + cidx + " should not be cached on " + H2O.SELF, H2O.STORE.get(ck));
                }
            }
        } finally {
            Scope.exit();
        }
    }

    @Test public void testParquetForceColTypes() {
        Scope.enter();
        try {
            final Frame data1 = parseTestFile("smalldata/parser/parquet/df.parquet", null, 1,
                    null, null, null, true);
            Scope.track(data1);
            Assert.assertFalse(data1.vec(1).isInt());
        } finally {
            Scope.exit();
        }
    }

}
