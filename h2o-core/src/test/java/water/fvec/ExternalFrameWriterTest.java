package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.util.ArrayUtils;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.sql.Timestamp;
import java.util.Calendar;

/**
 * Test external frame writer test
 */
public class ExternalFrameWriterTest extends TestUtil {
    @BeforeClass()
    public static void setup() {
        stall_till_cloudsize(3);
    }

    private void initFrame(String keyName, String[] names) {
        Frame fr = new water.fvec.Frame(Key.<Frame>make(keyName));
        fr.preparePartialFrame(names);
        // Save it directly to DKV
        fr.update();
    }

    private Frame finalizeFrame(String keyName, long[] rowsPerChunk, byte[] colTypes, String[][] colDomains){
        Frame fr = DKV.getGet(keyName);
        fr.finalizePartialFrame(rowsPerChunk, colDomains, colTypes);
        return fr;
    }

    @Test
    public void testWriting() throws IOException{
        final String[] nodes = new String[H2O.CLOUD._memary.length];

        // get ip and ports of h2o nodes
        for(int i = 0; i<nodes.length; i++){
            nodes[i] = H2O.CLOUD._memary[i].getIpPortString();
        }

        // we will open 2 connection per h2o node
        final String[] connStrings = ArrayUtils.join(nodes, nodes);

        // The api expects that empty frame has to be in the DKV before we start working with it
        final String frameName = "fr";
        String[] colNames = {"NUM", "BOOL", "STR", "TIMESTAMP"};
        final byte[] vecTypes = {Vec.T_NUM, Vec.T_NUM, Vec.T_STR, Vec.T_NUM};
        initFrame(frameName, colNames);
        final long[] rowsPerChunk = new long[connStrings.length]; // number of chunks will be number of h2o nodes

        Thread[] threads = new Thread[connStrings.length];

        // open all connections in connStrings array
        for(int idx = 0; idx<connStrings.length; idx++){
            final int currentIndex = idx;
            threads[idx] = new Thread(){
                @Override
                public void run() {
                    try {
                        SocketChannel sock = ExternalFrameHandler.getConnection(connStrings[currentIndex]);
                        ExternalFrameWriter writer = new ExternalFrameWriter(sock);
                        writer.createChunks(frameName, vecTypes, currentIndex);

                        for (int i = 0; i < 9999; i++) {
                            writer.put(0, i);
                            writer.put(1, true);
                            writer.put(2, "str_" + i);
                            writer.put(3, new Timestamp(Calendar.getInstance().getTime().getTime()));
                        }

                        writer.putNA(0);
                        writer.putNA(1);
                        writer.putNA(3);
                        writer.putNA(3);

                        writer.closeChunks();
                        sock.close();
                        rowsPerChunk[currentIndex] = 10000;
                    }catch (IOException ignore){}
                }

            };
            threads[idx].start();
        }

        // wait for all writer thread to finish
        for(Thread t: threads){
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        finalizeFrame(frameName, rowsPerChunk, vecTypes, null);

        Frame frame = DKV.getGet(frameName);
        assert( frame.anyVec().nChunks() == connStrings.length);
        assert (frame._names.length == 4);
        assert (frame.numCols() == 4);
        assert (frame._names[0].equals("NUM"));
        assert (frame._names[1].equals("BOOL"));
        assert (frame._names[2].equals("STR"));
        assert (frame._names[3].equals("TIMESTAMP"));
        assert (frame.vec(0)._type == Vec.T_NUM);
        assert (frame.vec(1)._type == Vec.T_NUM);
        assert (frame.vec(2)._type == Vec.T_STR);
        assert (frame.vec(3)._type == Vec.T_NUM);
        // last row should be NA
        assert (frame.anyVec().isNA(9999));
        assert (frame.numRows() == 10000 * connStrings.length);
        frame.remove();
    }
}
