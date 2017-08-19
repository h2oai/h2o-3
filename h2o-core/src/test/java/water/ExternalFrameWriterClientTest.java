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
import java.util.Random;

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
        // The api expects that empty frame has to be in the DKV before we start working with it
        final Timestamp time = new Timestamp(Calendar.getInstance().getTime().getTime());
        WriteOperation testOp = new WriteOperation() {
            @Override
            public void doWrite(ExternalFrameWriterClient writer) throws IOException {

                for (int i = 0; i < 997; i++) {
                    writer.sendInt(i);
                    writer.sendBoolean(true);
                    writer.sendString("str_" + i);
                    writer.sendTimestamp(time);
                }
                writer.sendInt(0);
                writer.sendBoolean(false);
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
            }

            @Override
            public int nrows() {
                return 1000;
            }

            @Override
            public String[] colNames() {
                return new String[] {"NUM", "BOOL", "STR", "TIMESTAMP"};
            }

            @Override
            public byte[] colTypes() {
                return new byte[] {
                    ExternalFrameUtils.EXPECTED_INT,
                    ExternalFrameUtils.EXPECTED_BOOL,
                    ExternalFrameUtils.EXPECTED_STRING,
                    ExternalFrameUtils.EXPECTED_TIMESTAMP};
            }
        };

        final String[] nodes = getH2ONodes();
        // we will open 2 connection per h2o node
        final String[] connStrings = ArrayUtils.join(nodes, nodes);

        Frame frame = createFrame(testOp, connStrings);
        try {
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
            
            BufferedString buff = new BufferedString();
            for (int i = 0; i < connStrings.length; i++) { // Writer segment (=chunk)
                for (int localRow = 0; localRow < 997; localRow++) { // Local row in segment
                    assertEquals(localRow, frame.vec(0).at8(localRow));
                    assertEquals(1, frame.vec(1).at8(localRow));
                    assertEquals("str_" + localRow, frame.vec(2).atStr(buff, localRow).toString());
                    assertEquals(time.getTime(), frame.vec(3).at8(localRow));
                }
                // Row 997
                int row = 997;
                assertEquals(0, frame.vec(0).at8(row));
                assertEquals(0, frame.vec(1).at8(row));
                assertTrue(frame.vec(2).isNA(row));
                assertEquals(time.getTime(), frame.vec(3).at8(row));
                // Row 998
                row = 998;
                assertEquals(1, frame.vec(0).at8(row));
                assertEquals(1, frame.vec(1).at8(row));
                assertEquals("\u0080" , frame.vec(2).atStr(buff, row).toString());
                assertEquals(time.getTime(), frame.vec(3).at8(row));
                // Row 999
                row = 999;
                for (int c = 0; c < 4; c++) {
                    assertTrue(frame.vec(c).isNA(row));
                }
            }
        } finally {
            frame.remove();
        }
    }

    @Test
    public void testDenseVectorWrite() throws IOException {
        WriteOperation testOp = new WriteOperation() {
            private final static int VEC_LEN = 100;
            @Override
            public void doWrite(ExternalFrameWriterClient writer) throws IOException {
                for (int i = 0; i < nrows(); i++) {
                    writer.sendDenseVector(vector(i, i, VEC_LEN));
                }
            }

            @Override
            public int nrows() {
                return 10;
            }

            @Override
            public String[] colNames() {
                return names("DV", VEC_LEN);
            }

            @Override
            public byte[] colTypes() {
                return new byte[] { ExternalFrameUtils.EXPECTED_VECTOR};
            }

            @Override
            public int[] maxVecSizes() {
                return new int[] {VEC_LEN};
            }
        };
        assertVectorWrite(testOp);
    }

    @Test
    public void testSparseVectorWrite() throws IOException {
        WriteOperation testOp = new WriteOperation() {
            private final static int VEC_LEN = 100;
            @Override
            public void doWrite(ExternalFrameWriterClient writer) throws IOException {
                for (int i = 0; i < nrows(); i++) {
                    writer.sendSparseVector(new int[] {i}, new double[]{i});
                }
            }

            @Override
            public int nrows() {
                return 10;
            }

            @Override
            public String[] colNames() {
                return names("SV", VEC_LEN);
            }

            @Override
            public byte[] colTypes() {
                return new byte[] { ExternalFrameUtils.EXPECTED_VECTOR};
            }

            @Override
            public int[] maxVecSizes() {
                return new int[] {VEC_LEN};
            }
        };
        assertVectorWrite(testOp);
    }


    static void assertVectorWrite(WriteOperation testOp) throws IOException {

        final String[] nodes = getH2ONodes();
        // we will open 2 connection per h2o node
        final String[] connStrings = ArrayUtils.join(nodes, nodes);

        Frame frame = createFrame(testOp, connStrings);
        try {
            assertEquals("Number of columns", testOp.colNames().length, frame.numCols());
            assertEquals("Number of rows", testOp.nrows() * connStrings.length, frame.numRows());
            for (int i = 0; i < connStrings.length; i++) { // Writer segment
                for (int localRow = 0; localRow < testOp.nrows(); localRow++) {
                    for (int c = 0; c < frame.numCols(); c++) { // Column
                        int globalRow = localRow + testOp.nrows()*i;
                        assertEquals(String.format("Values at position: globalRow=%d, localRow=%d, column=%d", globalRow, localRow, c),
                                   c == localRow ? c : 0.0, frame.vec(c).at(globalRow), 0.00001);
                    }
                }
            }
        } finally {
            frame.delete();
        }
    }

    static void joinThreads(Thread ...threads ) {
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    static String[] getH2ONodes() {
        final String[] nodes = new String[H2O.CLOUD._memary.length];

        // get ip and ports of h2o nodes
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = H2O.CLOUD._memary[i].getIpPortString();
        }
        return nodes;
    }

    static Frame createFrame(final WriteOperation op,
                             final String[] writeEndpoints) {
        ChunkUtils.initFrame(op.frameName(), op.colNames());
        final long[] rowsPerChunk = new long[writeEndpoints.length]; // number of chunks will be number of h2o nodes
        Thread[] threads = new Thread[writeEndpoints.length];

        for (int idx = 0; idx < writeEndpoints.length; idx++) {
            final int currentIndex = idx;
            threads[idx] = new Thread() {
                @Override
                public void run() {
                    try {
                        ByteChannel sock = ExternalFrameUtils.getConnection(writeEndpoints[currentIndex]);
                        try {
                            ExternalFrameWriterClient writer = new ExternalFrameWriterClient(sock);
                            writer.createChunks(op.frameName(), op.colTypes(),  currentIndex, op.nrows(), op.maxVecSizes());

                            op.doWrite(writer);

                            writer.waitUntilAllWritten(10);
                            
                            rowsPerChunk[currentIndex] = op.nrows();
                        } finally {
                            sock.close();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

            };
            threads[idx].start();
        }

        joinThreads(threads);

        ChunkUtils.finalizeFrame(op.frameName(), rowsPerChunk, ExternalFrameUtils.vecTypesFromExpectedTypes(op.colTypes(), op.maxVecSizes()), null);

        return DKV.getGet(op.frameName());
    }

    static double[] vector(double c, int idx, int len) {
        double[] v = new double[len];
        for (int i = 0; i < len; i++) v[i] = (i == idx) ? c : 0.0;
        return v;
    }

    static String[] names(String prefix, int len) {
        String[] names = new String[len];
        for (int i = 0; i < len; i++) names[i] = prefix + i;
        return names;
    }

}

abstract class WriteOperation {

    private final int idx = Math.abs(new Random().nextInt());

    abstract public void doWrite(ExternalFrameWriterClient writer) throws IOException;

    abstract public int nrows();

    public String frameName() {
        return "testFrame_" + idx;
    }

    abstract public String[] colNames();

    abstract public byte[] colTypes();

    public int[] maxVecSizes() {
        return null;
    }

}