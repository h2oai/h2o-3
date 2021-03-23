package water.persist;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.H2O;
import water.Key;
import water.Scope;
import water.fvec.C1NChunk;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class PersistHexTest {

    @Test
    public void testOpen() throws IOException {
        Persist ph = new PersistHex();
        Scope.enter();
        try {
            Vec v = Vec.makeConN(10, 1);
            Scope.track(v);
            Key chunkKey = v.chunkKey(0);

            byte[] data = new byte[v.chunkLen(0)];
            for (int i = 0; i < data.length; i++)
                data[i] = (byte) i;
            DKV.put(chunkKey, new C1NChunk(data));

            ByteArrayOutputStream output = new ByteArrayOutputStream(); 
            try (InputStream is = ph.open(H2O.getPM().toHexPath(chunkKey))) {
                IOUtils.copy(is, output);
                output.close();
            }
            assertArrayEquals(data, output.toByteArray());
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testCreate() throws IOException  {
        Persist ph = new PersistHex();
        Scope.enter();
        try {
            Vec v = Vec.makeConN(10, 1);
            Key chunkKey = v.chunkKey(0);
            Scope.track(v);

            byte[] newData = new byte[v.chunkLen(0)];
            for (int i = 0; i < newData.length; i++)
                newData[i] = (byte) i;
            try (OutputStream os = ph.create(H2O.getPM().toHexPath(chunkKey), true)) {
                os.write(newData);
            }
            Chunk c = v.chunkForChunkIdx(0);
            assertTrue(c instanceof C1NChunk);
            assertArrayEquals(newData, c.asBytes());
        } finally {
            Scope.exit();
        }
    }

}
