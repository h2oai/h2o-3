package water;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.sql.Timestamp;

import static water.ExternalFrameUtils.writeToChannel;

/**
 * This class is used to read data from H2O Frames from non-H2O environments, such as Spark Executors.
 *
 * It is expected that the frame we want to read is already in the DKV - the check is up on the user of this class.
 * Also the caller should be aware of frame structure and vector types.
 *
 * Example usage of this class:
 *
 * First we need to open the connection to H2O and initialize the reader
 * <pre>{@code
 * // select all columns
 * int[] selectedColumnIndices = {0, 1};
 * byte[] expectedTypes = {ExternalFrameHandler.EXPECTED_BOOL, ExternalFrameHandler.EXPECTED_INT}
 * ByteChannel channel = ExternalFrameUtils.getConnection("ip:port")
 * ExternalFrameReader reader = new ExternalFrameReader(channel, "frameName", 0, selectedColumnIndices);
 * }
 * </pre>
 *
 * Then we can read the data we expect, in our case boolean and integer
 * <pre>{@code
 * int rowsRead = 0;
 * while(rowsRead < reader.getNumRows){
 *  boolean b = reader.readBool()
 *  if(reader.isNA(b){
 *      ...
 *  }
 *
 *  int i = reader.readInt()
 *  if(reader.isNA(i){
 *      ...
 *  }
 * }
 * </pre>
 *
 * And at the end we need to make sure to force to code wait for all data to be read
 * <pre>{@code
 * reader.waitUntilAllReceived()
 * }
 * </pre>
 */
public class ExternalFrameReaderClient {

    private AutoBuffer ab;
    private String keyName;
    private int chunkIdx;
    private int[] selectedColumnIndices;
    private ByteChannel channel;
    private int numRows;
    private byte[] expectedTypes = null;

    public ExternalFrameReaderClient(ByteChannel channel, String keyName, int chunkIdx, byte[] expectedTypes, int[] selectedColumnIndices) throws IOException{
        this.channel = channel;
        this.keyName = keyName;
        this.chunkIdx = chunkIdx;
        this.expectedTypes = expectedTypes;
        this.selectedColumnIndices = selectedColumnIndices;
        this.ab = prepareAutoBuffer();
        // once we send H2O all information it needs to prepare for reading, it sends us back number of rows
        this.numRows = ab.getInt();
    }

    public int getNumRows(){
        return numRows;
    }

    public boolean readBoolean(){
        return ab.getZ();
    }
    public byte readByte(){
        return ab.get1();
    }
    public char readChar(){
        return ab.get2();
    }
    public short readShort(){
        return ab.get2s();
    }
    public int readInt(){
        return ab.getInt();
    }
    public long readLong(){
        return ab.get8();
    }
    public float readFloat(){
        return ab.get4f();
    }
    public double readDouble(){
        return ab.get8d();
    }
    public String readString(){
        return ab.getStr();
    }
    public Timestamp readTimeStamp(){
        return new Timestamp(ab.get8());
    }

    public boolean isNA(boolean data){
        return ExternalFrameUtils.isNA(ab, data);
    }

    public boolean isNA(byte data){
        return ExternalFrameUtils.isNA(ab, data);
    }

    public boolean isNA(short data){
        return ExternalFrameUtils.isNA(ab, data);
    }

    public boolean isNA(char data){
        return ExternalFrameUtils.isNA(ab, data);
    }

    public boolean isNA(int data){
        return ExternalFrameUtils.isNA(ab, data);
    }

    public boolean isNA(long data){
        return ExternalFrameUtils.isNA(ab, data);
    }

    public boolean isNA(float data){
        return ExternalFrameUtils.isNA(data);
    }

    public boolean isNA(double data){
        return ExternalFrameUtils.isNA(data);
    }

    public boolean isNA(Timestamp data){
        return ExternalFrameUtils.isNA(ab, data);
    }

    public boolean isNA(String data){
        return ExternalFrameUtils.isNA(ab, data);
    }

    /**
     * This method ensures the application waits for all bytes to be received before continuing in the control flow.
     *
     * It has to be called at the end of reading.
     */
    public void waitUntilAllReceived(){
        // confirm that all has been done before proceeding with the computation
        assert(ab.get1() == ExternalFrameHandler.CONFIRM_READING_DONE);
    }

    private AutoBuffer prepareAutoBuffer() throws IOException{
        AutoBuffer ab = new AutoBuffer();
        ab.put1(ExternalFrameHandler.INIT_BYTE);
        ab.put1(ExternalFrameHandler.DOWNLOAD_FRAME);
        ab.putStr(keyName);
        ab.putInt(chunkIdx);
        ab.putA1(expectedTypes);
        ab.putA4(selectedColumnIndices);
        writeToChannel(ab, channel);
        return new AutoBuffer(channel, null);
    }

}
