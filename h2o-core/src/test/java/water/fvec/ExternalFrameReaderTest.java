package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.HashMap;

public class ExternalFrameReaderTest extends TestUtil{
    @BeforeClass()
    public static void setup() {
        stall_till_cloudsize(2);
    }


    public class TestFrameBuilder{

        private HashMap<Integer, String[]> stringData = new HashMap<>();
        private HashMap<Integer, double[]> numericData = new HashMap<>();
        private String frameName;
        private byte[] vecTypes;
        private String[] colNames;
        private long[] chunkLayout;

        private void createChunks( long start, long length, int cidx){
            NewChunk[] nchunks = Frame.createNewChunks(frameName, vecTypes, cidx);
            for (int i=(int)start; i<start+length; i++) {

                for(int colIdx=0; colIdx<vecTypes.length; colIdx++){
                    switch (vecTypes[colIdx]){
                        case Vec.T_NUM:
                            nchunks[colIdx].addNum(numericData.get(colIdx)[i]);
                            break;
                        case Vec.T_STR:
                            nchunks[colIdx].addStr(stringData.get(colIdx)[i]);
                            break;
                        default:
                            throw new UnsupportedOperationException("Unsupported Vector type for the builder");

                    }
                }
            }
            Frame.closeNewChunks(nchunks);
        }

        public TestFrameBuilder withName(String frameName){
            this.frameName = frameName;
            return this;
        }

        public TestFrameBuilder withColNames(String... colNames){
            this.colNames = colNames;
            return this;
        }

        public TestFrameBuilder withVecTypes(byte... vecTypes){
            this.vecTypes = vecTypes;
            return this;
        }

        public TestFrameBuilder withDataForCol(int column, String[] data){
            stringData.put(column, data);
            return this;
        }

        public TestFrameBuilder withDataForCol(int column, double[] data){
            numericData.put(column, data);
            return this;
        }

        public TestFrameBuilder withDataForCol(int column, long[] data){
            double[] d = new double[data.length];
            for(int i = 0; i<data.length;i++){
                d[i] = data[i];
            }
            numericData.put(column, d);
            return this;
        }

        public TestFrameBuilder withChunkLayout(long... chunkLayout){
            this.chunkLayout = chunkLayout;
            return this;
        }


        public Frame build(){
            if(vecTypes == null){
                throw new RuntimeException("Vec types has to be specified");
            }

            Key key;
            if(frameName == null){
               key = Key.<Frame>make();
            }else{
                key = Key.<Frame>make(frameName);
            }

            String[] _colNames;
            if(colNames == null){
                _colNames = new String[vecTypes.length];
                for(int i=0; i<vecTypes.length;i++){
                    _colNames[i] = "col_"+i;
                }
            }else{
                _colNames = colNames;
            }

            // Create a frame
            Frame f = new Frame(key);
            f.preparePartialFrame(_colNames);
            f.update();

            // Create chunks
            int cidx = 0;
            long start = 0;
            for(int i=0; i<chunkLayout.length;i++) {
                createChunks(start, chunkLayout[i], cidx);
                cidx++;
                start = start + chunkLayout[i];
            }

            // Reload frame from DKV
            f = DKV.get(frameName).get();
            // Finalize frame
            f.finalizePartialFrame(chunkLayout, null, vecTypes);
            return f;
        }
    }

    private volatile AssertionError exc;

    @Test
    public void testReading() throws IOException, InterruptedException {
        final String frameName = "testFrame";
        final long[] chunkLayout = {2, 2, 2, 1};
        final Frame testFrame = new TestFrameBuilder()
                .withName(frameName)
                .withColNames("ColA", "ColB")
                .withVecTypes(Vec.T_NUM, Vec.T_STR)
                .withDataForCol(0, ard(Double.NaN, 1, 2, 3, 4, 5.6, 7))
                .withDataForCol(1, ar("A", "B", "C", "E", "F", "I", "J"))
                .withChunkLayout(chunkLayout)
                .build();


        // create frame
        final String[] nodes = new String[H2O.CLOUD._memary.length];
        // get ip and ports of h2o nodes
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = H2O.CLOUD._memary[i].getIpPortString();
        }

        final int nChunks = testFrame.anyVec().nChunks();

        final byte[] expectedTypes = {ExternalFrameReader.EXPECTED_DOUBLE, ExternalFrameReader.EXPECTED_STRING};

        // we will read from all chunks at the same time
        Thread[] threads = new Thread[nChunks];


        // open all connections in connStrings array
        for (int idx = 0; idx < nChunks; idx++) {
            final int currentChunkIdx = idx;
            threads[idx] = new Thread() {
                @Override
                public void run() {
                    try {
                        SocketChannel sock = ExternalFrameHandler.getConnection(nodes[currentChunkIdx % 2]);
                        ExternalFrameReader reader = new ExternalFrameReader(sock, frameName, expectedTypes, currentChunkIdx, new int[]{0,1});

                        int rowsRead = 0;
                        assert reader.getNumOfRows() == chunkLayout[currentChunkIdx];

                        while (rowsRead < reader.getNumOfRows()) {

                            if (rowsRead == 0 & currentChunkIdx == 0) {
                                boolean isNA = reader.readIsNA();
                                assert isNA: "[0,0] in chunk 0 should be NA";
                            } else {
                                if (!reader.readIsNA()) {
                                    reader.readDouble();
                                }
                            }

                            if (!reader.readIsNA()) {
                                reader.readString();
                            }
                            rowsRead++;
                        }

                        assert rowsRead == reader.getNumOfRows(): "Num or rows read was " + rowsRead + ", expecting "+ reader.getNumOfRows();

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
        for(Thread t: threads){
            t.join();
            if(exc != null){
                throw exc;
            }
        }


        testFrame.remove();
    }

}
