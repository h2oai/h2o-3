package water;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.sql.Timestamp;
import java.util.concurrent.*;

import static water.ExternalFrameUtils.writeToChannel;

/**
 * <p>This class is used to create and write data to H2O Frames from non-H2O environments, such as Spark Executors.</p>
 *
 * <strong>Example usage of this class:</strong></br>
 *
 * <p>First we need to open the connection to H2O and initialize the writer:
 * <pre>
 * {@code
 * // Prepare expected bytes from Java Classes.
 * // We don't specify vector types since they are deterministically inferred from the expected types
 * byte[] expectedBytes = ExternalFrameUtils.prepareExpectedTypes(new Class[]{Boolean.class, Integer.class});
 * ByteChannel channel = ExternalFrameUtils.getConnection("ip:port");
 * ExternalFrameWriter writer = new ExternalFrameWriter(channel);
 * writer.createChunks("frameName", expectedTypes, chunkIdx, numOfRowsToBeWritten);
 * }
 * </pre>
 * </p>
 *
 * <p>
 * Then we can write the data:
 * <pre>{@code
 * int rowsWritten = 0;
 * while(rowsWritten < totalNumOfRows){
 *  writer.sendBool(true);
 *  writer.sendInt(657);
 * }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * And at the end we need to make sure to force to code wait for all data to be written
 * <pre>
 * {@code
 * writer.waitUntilAllWritten();
 * }
 * </pre>
 * </p>
 */
final public class ExternalFrameWriterClient {

    private AutoBuffer ab;
    private ByteChannel channel;
    private byte[] expectedTypes;
    // we discover the current column index based on number of data sent
    private int currentColIdx = 0;

    /**
     * Initialize the External frame writer
     *
     * This method expects expected types in order to ensure we send the data in optimal way.
     * @param channel communication channel to h2o node
     */
    public ExternalFrameWriterClient(ByteChannel channel){
        this.ab = new AutoBuffer();
        this.channel = channel;
    }


    /**
     * Create chunks on the h2o backend. This method creates chunk in en empty frame.
     * @param frameKey name of the frame
     * @param expectedTypes expected types
     * @param chunkId chunk index
     * @param totalNumRows total number of rows which is about to be sent
     */
    public void createChunks(String frameKey, byte[] expectedTypes, int chunkId, int totalNumRows, int[] maxVecSizes) throws IOException {
        ab.put1(ExternalFrameHandler.INIT_BYTE);
        ab.put1(ExternalFrameHandler.CREATE_FRAME);
        ab.putStr(frameKey);
        this.expectedTypes = expectedTypes;
        ab.putA1(expectedTypes);
        ab.putA4(maxVecSizes);
        ab.putInt(totalNumRows);
        ab.putInt(chunkId);
        writeToChannel(ab, channel);
    }

    public void sendBoolean(boolean data) throws IOException{
        ExternalFrameUtils.sendBoolean(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendByte(byte data) throws IOException{
        ExternalFrameUtils.sendByte(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendChar(char data) throws IOException{
        ExternalFrameUtils.sendChar(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendShort(short data) throws IOException{
        ExternalFrameUtils.sendShort(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendInt(int data) throws IOException{
        ExternalFrameUtils.sendInt(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendLong(long data) throws IOException{
        ExternalFrameUtils.sendLong(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendFloat(float data) throws IOException{
        ExternalFrameUtils.sendFloat(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendDouble(double data) throws IOException{
        ExternalFrameUtils.sendDouble(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendString(String data) throws IOException{
        ExternalFrameUtils.sendString(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendTimestamp(Timestamp timestamp) throws IOException{
        ExternalFrameUtils.sendTimestamp(ab, channel, timestamp);
        increaseCurrentColIdx();
    }

    public void sendNA() throws IOException{
        ExternalFrameUtils.sendNA(ab, channel, expectedTypes[currentColIdx]);
        increaseCurrentColIdx();
    }

    public void sendSparseVector(int[] indices, double[] values) throws IOException {
        sendBoolean(ExternalFrameUtils.VECTOR_IS_SPARSE);
        ExternalFrameUtils.sendIntArray(ab, channel, indices);
        ExternalFrameUtils.sendDoubleArray(ab, channel, values);
    }

    public void sendDenseVector(double[] values) throws IOException {
        sendBoolean(ExternalFrameUtils.VECTOR_IS_DENSE);
        ExternalFrameUtils.sendDoubleArray(ab, channel, values);
    }

    /**
     * This method ensures the application waits for all bytes to be written before continuing in the control flow.
     *
     * It has to be called at the end of writing.
     * @param timeout timeout in seconds
     * @throws ExternalFrameConfirmationException
     */
    public void waitUntilAllWritten(int timeout) throws ExternalFrameConfirmationException {
        try {
            final AutoBuffer confirmAb = new AutoBuffer(channel, null);
            try {
                byte flag = ExternalFrameConfirmationCheck.getConfirmation(confirmAb, timeout);
                assert (flag == ExternalFrameHandler.CONFIRM_WRITING_DONE);
            } catch (TimeoutException ex) {
                throw new ExternalFrameConfirmationException("Timeout for confirmation exceeded!");
            } catch (InterruptedException e) {
                throw new ExternalFrameConfirmationException("Confirmation thread interrupted!");
            } catch (ExecutionException e) {
                throw new ExternalFrameConfirmationException("Confirmation failed!");
            }
        } catch (IOException e) {
            throw new ExternalFrameConfirmationException("Confirmation failed");
        }
    }

    private void increaseCurrentColIdx(){
        currentColIdx = (currentColIdx + 1) % expectedTypes.length;
    }
}
