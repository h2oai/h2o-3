package water;


import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * <p>This class is used to coordinate the requests for accessing/obtaining H2O frames from non-H2O environments
 * ( ie. Spark Executors).</p>
 *
 * <p>The user should use {@code getConnection} method to open a connection to an H2O node. This method creates the socket channel
 * in a way h2o internals expect it.</p>
 *
 * <p>The data can be written to h2o frame and read data from h2o frame.
 * When writing, it is expected that empty H2O frame is already in DKV before using the writing API. The caller
 * is responsible for finishing the frame once all data has been written to the frame. To read more about the writing
 * API, please read documentation of {@link ExternalFrameWriterClient}</p>
 *
 * <p>When reading the data, it is expected that h2o frame is in DKV. To read more about the reading API, please read
 * documentation of {@link ExternalFrameReaderClient}</p>
 *
 *
 */
final class ExternalFrameHandler {

    /**
     * This is used to inform us that another byte is coming.
     * That byte can be either {@code MARKER_ORIGINAL_VALUE} or {@code MARKER_NA}. If it's
     * {@code MARKER_ORIGINAL_VALUE}, that means
     * the value sent is in the previous data sent, otherwise the value is NA.
     */
    static final byte NUM_MARKER_NEXT_BYTE_FOLLOWS = 127;

    /**
     * Same as above, but for Strings. We are using unicode code for CONTROL, which should be very very rare
     * String to send as usual String data.
     */
    static final String STR_MARKER_NEXT_BYTE_FOLLOWS = "\u0080";

    /**
     *  Marker informing us that the data are not NA and are stored in the previous byte
     */
    static final byte MARKER_ORIGINAL_VALUE = 0;

    /**
     * Marker informing us that the data being sent is NA
     */
    static final byte MARKER_NA = 1;

    /** Byte signaling that new communication has been started on a existing/newly created socket channel
     *  Since connections can reused at the caller site ( for example spark executor ) we have to identify whether the
     *  the connection has been reused for sending more data or not
     * */
    static final byte INIT_BYTE = 42;

    /**
     * Bytes used for signaling that either reading from h2o frame or writing to h2o frame has finished.
     * It is important for these 2 bytes to be different, otherwise we could confirm writing by reading byte, which
     * would lead to unwanted states.
     */
    static final byte CONFIRM_READING_DONE = 1;
    static final byte CONFIRM_WRITING_DONE = 2;

    /**
     * Main task codes
     */
    static final byte CREATE_FRAME = 0;
    static final byte DOWNLOAD_FRAME = 1;

    /**
     * Method which receives the {@link SocketChannel} and {@link AutoBuffer} and dispatches the request for further processing
     */
    void process(SocketChannel sock, AutoBuffer ab) throws IOException {
        int requestType = ab.get1();
        switch (requestType) {
            case CREATE_FRAME:
                ExternalFrameWriterBackend.handleWriteToChunk(sock, ab);
                break;
            case DOWNLOAD_FRAME:
                ExternalFrameReaderBackend.handleReadingFromChunk(sock, ab);
                break;
        }
    }
}
