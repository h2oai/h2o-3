package water;

import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import water.fvec.ChunkUtils;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.ArrayUtils;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.sql.Timestamp;
import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test external frame writer test
 */
public class ExternalFrameWriterClientTest extends TestUtil {
    @BeforeClass()
    public static void setup() {
        stall_till_cloudsize(3);
    }

    @Test
    public void testWriting() throws IOException{
        final String[] nodes = new String[H2O.CLOUD._memary.length];

        // get ip and ports of h2o nodes
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = H2O.CLOUD._memary[i].getIpPortString();
        }

        // we will open 2 connection per h2o node
        final String[] connStrings = ArrayUtils.join(nodes, nodes);

        // The api expects that empty frame has to be in the DKV before we start working with it
        final String frameName = "fr";
        String[] colNames = {"NUM", "BOOL", "STR", "TIMESTAMP"};

        // vector types are inferred from expected types
        final byte[] expectedTypes = ExternalFrameUtils.prepareExpectedTypes(new Class[]{
                Integer.class,
                Boolean.class,
                String.class,
                Timestamp.class});


        ChunkUtils.initFrame(frameName, colNames);
        final long[] rowsPerChunk = new long[connStrings.length]; // number of chunks will be number of h2o nodes

        Thread[] threads = new Thread[connStrings.length];

        // open all connections in connStrings array
        for (int idx = 0; idx < connStrings.length; idx++) {
            final int currentIndex = idx;
            threads[idx] = new Thread() {
                @Override
                public void run() {
                    try {
                        ByteChannel sock = ExternalFrameUtils.getConnection(connStrings[currentIndex]);
                        ExternalFrameWriterClient writer = new ExternalFrameWriterClient(sock);
                        writer.createChunks(frameName, expectedTypes,  currentIndex, 1000);

                        Timestamp time = new Timestamp(Calendar.getInstance().getTime().getTime());
                        for (int i = 0; i < 997; i++) {
                            writer.sendInt(i);
                            writer.sendBoolean(true);
                            writer.sendString("str_" + i);
                            writer.sendTimestamp(time);
                        }
                        writer.sendInt(0);
                        writer.sendBoolean(true);
                        writer.sendString(null);
                        writer.sendTimestamp(time);

                        writer.sendInt(1);
                        writer.sendBoolean(true);
                        writer.sendString("\u0080");
                        writer.sendTimestamp(time);

                        // send NA for all columns
                        writer.sendNA();
                        writer.sendNA();
                        writer.sendNA();
                        writer.sendNA();

                        writer.waitUntilAllWriten();
                        sock.close();
                        rowsPerChunk[currentIndex] = 1000;
                    } catch (IOException ignore) {
                    }
                }

            };
            threads[idx].start();
        }

        // wait for all writer thread to finish
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


        ChunkUtils.finalizeFrame(frameName, rowsPerChunk, ExternalFrameUtils.vecTypesFromExpectedTypes(expectedTypes), null);

        Frame frame = null;
        try {
            frame = DKV.getGet(frameName);
            assertEquals(frame.anyVec().nChunks(), connStrings.length);
            assertEquals(frame._names.length, 4);
            assertEquals(frame.numCols(), 4);
            assertEquals(frame._names[0], "NUM");
            assertEquals(frame._names[1], "BOOL");
            assertEquals(frame._names[2], "STR");
            assertEquals(frame._names[3], "TIMESTAMP");
            assertEquals(frame.vec(0).get_type(), Vec.T_NUM);
            assertEquals(frame.vec(1).get_type(), Vec.T_NUM);
            assertEquals(frame.vec(2).get_type(), Vec.T_STR);
            assertEquals(frame.vec(3).get_type(), Vec.T_TIME);
            assertEquals(frame.numRows(), 1000 * connStrings.length);
            // last row should be NA
            assertEquals(frame.vec(0).at8(0), 0);

            BufferedString buff = new BufferedString();
            assertEquals(frame.vec(2).atStr(buff, 996).toString(), "str_996");
            assertEquals(frame.vec(2).atStr(buff, 997), null);
            assertEquals(frame.vec(2).atStr(buff, 998).toString(), "\u0080");
            assertTrue(frame.vec(0).isNA(999));
            assertTrue(frame.vec(1).isNA(999));
            assertTrue(frame.vec(2).isNA(999));
            assertTrue(frame.vec(3).isNA(999));

        }finally {
            if(frame != null){
                frame.remove();
            }
        }
    }
}
