package water;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

/**
 * Add chunks and data to non-finalized frame from non-h2o environment (ie. Spark executors)
 */
public class ExternalFrameHandler {

    static final byte INIT_BYTE = 42;
    static final byte CONFIRM_READING_DONE = 1;
    static final byte CONFIRM_WRITING_DONE = 2;

    // main tasks
    static final byte CREATE_FRAME = 0;
    static final byte DOWNLOAD_FRAME = 1;

    void process(SocketChannel sock, AutoBuffer ab) throws IOException {
        int requestType = ab.getInt();
        switch (requestType) {
            case CREATE_FRAME:
               ExternalFrameWriter.handleWriteToChunk(sock, ab);
                break;
            case DOWNLOAD_FRAME:
                ExternalFrameReader.handleReadingFromChunk(sock, ab);
                break;
        }
    }

    /**
     * Get and configure connection to specific h2o node
     * @param h2oNodeHostname
     * @param h2oNodeApiPort
     * @return
     * @throws IOException
     */
    public static SocketChannel getConnection(String h2oNodeHostname, int h2oNodeApiPort) throws IOException{
        SocketChannel sock = SocketChannel.open();
        sock.socket().setSendBufferSize(AutoBuffer.BBP_BIG._size);
        InetSocketAddress isa = new InetSocketAddress(h2oNodeHostname, h2oNodeApiPort + 1); // +1 to connect to internal comm port
        boolean res = sock.connect(isa); // Can toss IOEx, esp if other node is still booting up
        assert(res);
        sock.configureBlocking(true);
        assert(!sock.isConnectionPending() && sock.isBlocking() && sock.isConnected() && sock.isOpen());
        sock.socket().setTcpNoDelay(true);
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
        bb.put((byte)3).putChar((char)sock.socket().getLocalPort()).put((byte)0xef).flip();
        while (bb.hasRemaining()) {
            // Write out magic startup sequence
            sock.write(bb);
        }
        return sock;
    }

    public static SocketChannel getConnection(String ipPort) throws IOException{
        String[] split = ipPort.split(":");
        return getConnection(split[0], Integer.parseInt(split[1]));
    }
}
