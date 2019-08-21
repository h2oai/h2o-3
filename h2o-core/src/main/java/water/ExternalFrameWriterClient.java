package water;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.sql.Timestamp;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static water.ExternalFrameUtils.writeToChannel;

/**
 * <p>
 * This class is used to create and write data to H2O Frames from
 * non-H2O environments, such as Spark Executors.
 * </p>
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
 * 
 * writer.initFrame("frameName", columns)
 * writer.createChunk("frameName", expectedTypes, chunkIdx, numOfRowsToBeWritten);
 * </pre>
 * </p>
 *
 *
 * <p> Then we can write the data:
 * <pre>
 * {@code
 * int rowsWritten = 0;
 * while(rowsWritten < totalNumOfRows){
 *     writer.sendBool(true);
 *     writer.sendInt(657);
 *     rowsWritten++;
 *   }
 * }
 * </pre>
 * </p>
 * 
 * <p> At last, finalize the frame
 * <pre>
 * writer.finalizeFrame("frameName, rowsPerChunk, colTypes, domains);
 * </pre>
 * </p>
 *
 */
final public class ExternalFrameWriterClient {
  private AutoBuffer ab;
  private ByteChannel channel;
  private byte[] expectedTypes;
  private int currentColIdx = 0;
  private int currentRowIdx = 0;
  private int timeout;
  private int numRows;
  private int numCols;

  /**
   * Initialize the External frame writer
   * <p>
   * This method expects expected types in order to ensure we send the data in optimal way.
   *
   * @param channel communication channel to h2o node
   */
  public ExternalFrameWriterClient(ByteChannel channel, int timeout) {
    this.ab = new AutoBuffer();
    this.channel = channel;
    this.timeout = timeout;
  }

  public static ExternalFrameWriterClient create(String ip, int port, short timestamp, int timeout) throws IOException {
    ByteChannel channel = ExternalFrameUtils.getConnection(ip, port, timestamp);
    return new ExternalFrameWriterClient(channel, timeout);
  }

  public void initFrame(String keyName, String[] names) throws IOException, ExternalFrameConfirmationException {
    byte requestType = ExternalBackendRequestType.INIT_FRAME.getByte();
    ab.put1(ExternalFrameHandler.INIT_BYTE);
    ab.put1(requestType);
    ab.putStr(keyName);
    ab.putAStr(names);
    writeToChannel(ab, channel);
    waitForRequestToFinish(timeout, requestType);
  }

  /**
   * Create single chunk on the already existing empty frame.
   *
   * @param frameKey      name of the frame
   * @param expectedTypes expected types
   * @param chunkId       chunk index
   * @param totalNumRows  total number of rows which is about to be sent
   */
  public void createChunk(String frameKey, byte[] expectedTypes, int chunkId, int totalNumRows, int[] maxVecSizes) throws IOException {
    ab.put1(ExternalFrameHandler.INIT_BYTE);
    ab.put1(ExternalBackendRequestType.WRITE_TO_CHUNK.getByte());
    ab.putStr(frameKey);
    this.expectedTypes = expectedTypes;
    this.numCols = expectedTypes.length;
    this.numRows = totalNumRows;
    ab.putA1(expectedTypes);
    ab.putA4(maxVecSizes);
    ab.putInt(totalNumRows);
    ab.putInt(chunkId);
    writeToChannel(ab, channel);
  }
  
  public void finalizeFrame(String keyName, long[] rowsPerChunk, byte[] colTypes, String[][] domains)
      throws IOException, ExternalFrameConfirmationException {
    byte requestType = ExternalBackendRequestType.FINALIZE_FRAME.getByte();
    ab.put1(ExternalFrameHandler.INIT_BYTE);
    ab.put1(requestType);
    ab.putStr(keyName);
    ab.putA8(rowsPerChunk);
    ab.putA1(colTypes);
    ab.putAAStr(domains);
    writeToChannel(ab, channel);
    waitForRequestToFinish(timeout, requestType);
  }

    public void close() throws IOException {
        channel.close();
    }

    public void sendBoolean(boolean data) throws IOException, ExternalFrameConfirmationException {
        ExternalFrameUtils.sendBoolean(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendByte(byte data) throws IOException, ExternalFrameConfirmationException {
        ExternalFrameUtils.sendByte(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendChar(char data) throws IOException, ExternalFrameConfirmationException {
        ExternalFrameUtils.sendChar(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendShort(short data) throws IOException, ExternalFrameConfirmationException {
        ExternalFrameUtils.sendShort(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendInt(int data) throws IOException, ExternalFrameConfirmationException {
        ExternalFrameUtils.sendInt(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendLong(long data) throws IOException, ExternalFrameConfirmationException {
        ExternalFrameUtils.sendLong(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendFloat(float data) throws IOException, ExternalFrameConfirmationException {
        ExternalFrameUtils.sendFloat(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendDouble(double data) throws IOException, ExternalFrameConfirmationException {
        ExternalFrameUtils.sendDouble(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendString(String data) throws IOException, ExternalFrameConfirmationException {
        ExternalFrameUtils.sendString(ab, channel, data);
        increaseCurrentColIdx();
    }

    public void sendTimestamp(Timestamp timestamp) throws IOException, ExternalFrameConfirmationException {
        ExternalFrameUtils.sendTimestamp(ab, channel, timestamp);
        increaseCurrentColIdx();
    }

    public void sendNA() throws IOException, ExternalFrameConfirmationException {
        ExternalFrameUtils.sendNA(ab, channel, expectedTypes[currentColIdx]);
        increaseCurrentColIdx();
    }

  public void sendSparseVector(int[] indices, double[] values) throws IOException, ExternalFrameConfirmationException {
    ExternalFrameUtils.sendBoolean(ab, channel, ExternalFrameUtils.VECTOR_IS_SPARSE);
    ExternalFrameUtils.sendIntArray(ab, channel, indices);
    ExternalFrameUtils.sendDoubleArray(ab, channel, values);
    increaseCurrentColIdx();
  }

  public void sendDenseVector(double[] values) throws IOException, ExternalFrameConfirmationException {
    ExternalFrameUtils.sendBoolean(ab, channel, ExternalFrameUtils.VECTOR_IS_DENSE);
    ExternalFrameUtils.sendDoubleArray(ab, channel, values);
    increaseCurrentColIdx();
  }

  public int getNumberOfWrittenRows(){
    return currentRowIdx;
  }

  private void waitForRequestToFinish(int timeout, byte confirmation) throws ExternalFrameConfirmationException {
    final AutoBuffer confirmAb = new AutoBuffer(channel);
    try {
      byte flag = ExternalFrameConfirmationCheck.getConfirmation(confirmAb, timeout);
      assert (flag == confirmation);
    } catch (TimeoutException ex) {
      throw new ExternalFrameConfirmationException("Timeout for confirmation exceeded!");
    } catch (InterruptedException e) {
      throw new ExternalFrameConfirmationException("Confirmation thread interrupted!");
    } catch (ExecutionException e) {
      throw new ExternalFrameConfirmationException("Confirmation failed!");
    }
  }

  private void increaseCurrentColIdx() throws IOException, ExternalFrameConfirmationException {
    currentColIdx++;
    if (currentColIdx == numCols) {
      currentRowIdx++;
      if (currentRowIdx == numRows) {
        writeToChannel(ab, channel);
        waitForRequestToFinish(timeout, ExternalBackendRequestType.WRITE_TO_CHUNK.getByte());
      }

      currentColIdx = 0;
    }
  }
}
