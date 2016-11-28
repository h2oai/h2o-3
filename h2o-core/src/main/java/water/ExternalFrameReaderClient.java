package water;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.sql.Timestamp;

import static water.ExternalFrameUtils.writeToChannel;

/**
 * <p>This class is used to read data from H2O Frames from non-H2O environments, such as Spark Executors.
 * It is expected that the frame we want to read is already in the DKV. The check for the presence is up on the
 * user of this class.<p>
 *
 * <strong>Example usage of this class:</strong></br>
 *
 * <p>
 * First we need to open the connection to H2O and initialize the reader:</br>
 * <pre>
 * {@code
 * // specify indexes of columns we want to read data from
 * int[] selectedColumnIndices = {0, 1};
 * // specify expected types for the selected columns
 * byte[] expectedTypes = {ExternalFrameHandler.EXPECTED_BOOL, ExternalFrameHandler.EXPECTED_INT};
 * ByteChannel channel = ExternalFrameUtils.getConnection("ip:port");
 * ExternalFrameReader reader = new ExternalFrameReader(channel, "frameName", 0, selectedColumnIndices);
 * }
 * </pre>
 * </p>
 *
 * <p>
 * In the next step we can read the data we expect, in our case boolean and integer:</br>
 * <pre>
 * {@code
 * int rowsRead = 0;
 * while(rowsRead < reader.getNumRows){
 *     boolean b = reader.readBool();
 *     if(reader.isLastNA{
 *         // it is NA
 *     }else{
 *         // it is value
 *     }
 *
 *     int i = reader.readInt()
 *     if(reader.isLastNA{
 *     // it is NA
 *     }else{
 *         // it is value
 *     }
 * }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * And at the end we need to make sure to force to code wait for all data to be read:</br>
 * <pre>
 * {@code
 * reader.waitUntilAllReceived();
 * }
 * </pre>
 * </p>
 */
final public class ExternalFrameReaderClient {

    private boolean isLastNA = false;
    private AutoBuffer ab;
    private String frameKey;
    private int chunkIdx;
    private int[] selectedColumnIndices;
    private ByteChannel channel;
    private int numRows;
    private byte[] expectedTypes = null;

    /**
     *
     * @param channel channel to h2o node
     * @param frameKey name of frame we want to read from
     * @param chunkIdx chunk index from we want to read
     * @param selectedColumnIndices indices of columns we want to read from
     * @param expectedTypes expected types for
     */
    public ExternalFrameReaderClient(ByteChannel channel, String frameKey, int chunkIdx, int[] selectedColumnIndices, byte[] expectedTypes) throws IOException{
        this.channel = channel;
        this.frameKey = frameKey;
        this.chunkIdx = chunkIdx;
        this.expectedTypes = expectedTypes;
        this.selectedColumnIndices = selectedColumnIndices;
        this.ab = initAndGetAb();
    }

    public int getNumRows(){
        return numRows;
    }

    public boolean readBoolean(){
        boolean data = ab.getZ();
        isLastNA = ExternalFrameUtils.isNA(ab, data);
        return data;
    }

    public byte readByte(){
        byte data = ab.get1();
        isLastNA = ExternalFrameUtils.isNA(ab, data);
        return data;
    }
    public char readChar(){
        char data = ab.get2();
        isLastNA = ExternalFrameUtils.isNA(ab, data);
        return data;
    }
    public short readShort(){
        short data = ab.get2s();
        isLastNA = ExternalFrameUtils.isNA(ab, data);
        return data;
    }
    public int readInt(){
        int data = ab.getInt();
        isLastNA = ExternalFrameUtils.isNA(ab, data);
        return data;
    }
    public long readLong(){
        long data = ab.get8();
        isLastNA = ExternalFrameUtils.isNA(ab, data);
        return data;
    }
    public float readFloat(){
        float data = ab.get4f();
        isLastNA = ExternalFrameUtils.isNA(data);
        return data;
    }
    public double readDouble(){
        double data = ab.get8d();
        isLastNA = ExternalFrameUtils.isNA(data);
        return data;
    }
    public String readString(){
        String data = ab.getStr();
        isLastNA = ExternalFrameUtils.isNA(ab, data);
        return data;
    }
    public Timestamp readTimestamp(){
        Timestamp data = new Timestamp(ab.get8());
        isLastNA = ExternalFrameUtils.isNA(ab, data);
        return data;
    }

    /**
     * This method is used to check if the last received value was marked as NA by H2O backend
     */
    public boolean isLastNA(){
        return isLastNA;
    }

    /**
     * This method ensures the application waits for all bytes to be received before continuing in the
     * application's control flow.
     *
     * It has to be called at the end of reading.
     */
    public void waitUntilAllReceived(){
        // blocking call
        byte controlByte = ab.get1();
        assert(controlByte == ExternalFrameHandler.CONFIRM_READING_DONE);
    }

    private AutoBuffer initAndGetAb() throws IOException{
        AutoBuffer sentAb = new AutoBuffer();
        sentAb.put1(ExternalFrameHandler.INIT_BYTE);
        sentAb.put1(ExternalFrameHandler.DOWNLOAD_FRAME);
        sentAb.putStr(frameKey);
        sentAb.putInt(chunkIdx);
        sentAb.putA1(expectedTypes);
        sentAb.putA4(selectedColumnIndices);
        writeToChannel(sentAb, channel);
        AutoBuffer receiveAb = new AutoBuffer(channel, null);
        // once we send H2O all information it needs to prepare for reading, it sends us back number of rows
        this.numRows = receiveAb.getInt();
        return receiveAb;
    }
}
