package water;

import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.io.IOException;
import java.nio.channels.ByteChannel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExternalFrameReaderClientTest extends TestUtil{
    @BeforeClass()
    public static void setup() {
        stall_till_cloudsize(3);
    }

    // We keep this one for assertion errors occurred in different threads
    private volatile AssertionError exc;

    @Test
    public void testReading() throws IOException, InterruptedException {

        final String frameName = "testFrame";
        final long[] chunkLayout = {2, 2, 2, 1};
        final Frame testFrame = new TestFrameBuilder()
                .withName(frameName)
                .withColNames("ColA", "ColB")
                .withVecTypes(Vec.T_NUM, Vec.T_STR)
                .withDataForCol(0, ard(Double.NaN, 1, 2, 3, 4, 5.6, 7, -1, 3.14))
                .withDataForCol(1, ar("A", "B", "C", "E", "F", "I", "J", "\u0000", null))
                .withChunkLayout(chunkLayout)
                .build();


        // create frame
        final String[] nodes = new String[H2O.CLOUD._memary.length];
        // get ip and ports of h2o nodes
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = H2O.CLOUD._memary[i].getIpPortString();
        }

        final int[] selectedColumnIndices = {0, 1};
        // specify expected types for selected columns
        final byte[] expectedTypes = {ExternalFrameUtils.EXPECTED_DOUBLE, ExternalFrameUtils.EXPECTED_STRING};
        final int nChunks = testFrame.anyVec().nChunks();

        // we will read from all chunks at the same time
        Thread[] threads = new Thread[nChunks];

        try {
            // open all connections in connStrings array
            for (int idx = 0; idx < nChunks; idx++) {
                final int currentChunkIdx = idx;
                threads[idx] = new Thread() {
                    @Override
                    public void run() {
                        try {
                            ByteChannel sock = ExternalFrameUtils.getConnection(nodes[currentChunkIdx % nodes.length]);
                            ExternalFrameReaderClient reader = new ExternalFrameReaderClient(sock, frameName, currentChunkIdx, selectedColumnIndices, expectedTypes);

                            int rowsRead = 0;
                            assertEquals(reader.getNumRows(), chunkLayout[currentChunkIdx]);

                            while (rowsRead < reader.getNumRows()) {

                                if (rowsRead == 0 & currentChunkIdx == 0) {
                                    reader.readDouble();
                                    assertTrue("[0,0] in chunk 0 should be NA", reader.isLastNA());
                                } else {
                                    reader.readDouble();
                                    assertFalse("Should not be NA", reader.isLastNA());
                                }

                                reader.readString();
                                assertFalse("Should not be NA", reader.isLastNA());

                                rowsRead++;
                            }

                            assertEquals("Num or rows read was " + rowsRead + ", expecting " + reader.getNumRows(), rowsRead, reader.getNumRows());

                            reader.waitUntilAllReceived();
                            sock.close();
                        } catch (AssertionError e) {
                            exc = e;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
                threads[idx].start();
            }

            // wait for all writer thread to finish
            for (Thread t : threads) {
                t.join();
                if (exc != null) {
                    throw exc;
                }
            }
        }finally {
            testFrame.remove();
        }
    }

}
