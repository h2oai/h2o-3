package water.network;

import water.H2O;
import water.util.Log;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;

/**
 * This class is based on:
 * <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html">Oracle's JSSE guide.</a>
 * <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/samples/sslengine/SSLEngineSimpleDemo.java">Oracle's SSLEngine demo.</a>
 *
 * It's a simple wrapper around SocketChannels which enables SSL/TLS
 * communication using {@link javax.net.ssl.SSLEngine}.
 */
class SSLSocketChannel implements ByteChannel {

    // Empty buffer for handshakes
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    // Buffer holding encrypted outgoing data
    private ByteBuffer netInBuffer;
    // Buffer holding encrypted incoming data
    private ByteBuffer netOutBuffer;

    // Buffer holding decrypted incoming data
    private ByteBuffer peerAppData;

    private SocketChannel channel = null;
    private SSLEngine sslEngine = null;

    private boolean closing = false;
    private boolean closed = false;

    private boolean handshakeComplete = false;

    SSLSocketChannel(SocketChannel channel, SSLEngine sslEngine) throws IOException {
        this.channel = channel;
        this.sslEngine = sslEngine;

        sslEngine.setEnableSessionCreation(true);
        SSLSession session = sslEngine.getSession();
        prepareBuffers(session);

        handshake();
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        closing = true;
        sslEngine.closeOutbound();
        sslEngine.getSession().invalidate();
        netOutBuffer.clear();
        channel.close();
        closed = true;
    }

    private void prepareBuffers(SSLSession session) throws SocketException {
        int appBufferSize = session.getApplicationBufferSize();
        // Less is not more. More is more. Bigger than the app buffer size so successful unwraps() don't cause BUFFER_OVERFLOW
        // Value 64 was based on other frameworks using it and some manual testing. Might require tuning in the future.
        peerAppData = ByteBuffer.allocate(appBufferSize + 64);

        int netBufferSize = session.getPacketBufferSize();
        netInBuffer = ByteBuffer.allocate(netBufferSize);
        netOutBuffer = ByteBuffer.allocate(netBufferSize);

    }

    // -----------------------------------------------------------
    // HANDSHAKE
    // -----------------------------------------------------------

    private SSLEngineResult.HandshakeStatus hs;

    private void handshake() throws IOException {
        Log.debug("Starting SSL handshake...");
        sslEngine.beginHandshake();

        hs = sslEngine.getHandshakeStatus();
        SSLEngineResult initHandshakeStatus;

        while (!handshakeComplete) {
            switch (hs) {
                case NOT_HANDSHAKING: {
                    //should never happen
                    throw new IOException("NOT_HANDSHAKING during handshake");
                }
                case FINISHED:
                    handshakeComplete = !netOutBuffer.hasRemaining();
                    break;
                case NEED_WRAP: {
                    initHandshakeStatus = handshakeWrap();
                    if ( initHandshakeStatus.getStatus() == SSLEngineResult.Status.OK ){
                        if (hs == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                            tasks();
                        }
                    }
                    break;
                }
                case NEED_UNWRAP: {
                    initHandshakeStatus = handshakeUnwrap();
                    if ( initHandshakeStatus.getStatus() == SSLEngineResult.Status.OK ){
                        if (hs == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                            tasks();
                        }
                    }
                    break;
                }
                // SSL needs to perform some delegating tasks before it can continue.
                // Those tasks will be run in the same thread and can be blocking.
                case NEED_TASK:
                    tasks();
                    break;
            }
        }
        Log.debug("SSL handshake finished successfully!");
    }

    private synchronized SSLEngineResult handshakeWrap() throws IOException {
        netOutBuffer.clear();
        SSLEngineResult wrapResult = sslEngine.wrap(EMPTY_BUFFER, netOutBuffer);
        netOutBuffer.flip();
        hs = wrapResult.getHandshakeStatus();
        channel.write(netOutBuffer);
        return wrapResult;
    }

    private synchronized SSLEngineResult handshakeUnwrap() throws IOException {
        if (netInBuffer.position() == netInBuffer.limit()) {
            netInBuffer.clear();
        }

        channel.read(netInBuffer);

        SSLEngineResult unwrapResult;
        peerAppData.clear();

        do {
            netInBuffer.flip();

            unwrapResult = sslEngine.unwrap(netInBuffer, peerAppData);

            netInBuffer.compact();

            hs = unwrapResult.getHandshakeStatus();

            switch (unwrapResult.getStatus()) {
                case OK:
                case BUFFER_UNDERFLOW: {
                    if (unwrapResult.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                        tasks();
                    }
                    break;
                }
                case BUFFER_OVERFLOW: {
                    int applicationBufferSize = sslEngine.getSession().getApplicationBufferSize();
                    if (applicationBufferSize > peerAppData.capacity()) {
                        ByteBuffer b = ByteBuffer.allocate(applicationBufferSize + peerAppData.position());
                        peerAppData.flip();
                        b.put(peerAppData);
                        peerAppData = b;
                    } else {
                        peerAppData.compact();
                    }
                    break;
                }
                default:
                    throw new IOException("Failed to SSL unwrap with status " + unwrapResult.getStatus());
            }
        } while(unwrapResult.getStatus() == SSLEngineResult.Status.OK &&
                hs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP);

        return unwrapResult;
    }

    // -----------------------------------------------------------
    // READ AND WRITE
    // -----------------------------------------------------------

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (closing || closed) return -1;

        return unwrap(dst);
    }

    private synchronized int unwrap(ByteBuffer dst) throws IOException {
        int read = 0;
        // We have outstanding data in our incoming decrypted buffer, use that data first to fill dst
        if(!dst.hasRemaining()) {
            return 0;
        }

        if(peerAppData.position() != 0) {
            read += copy(peerAppData, dst);
            return read;
        }

        if(netInBuffer.position() == 0) {
            channel.read(netInBuffer);
        }

        while(netInBuffer.position() != 0) {
            netInBuffer.flip();

            // We still might have left data here if dst was smaller than the amount of data in peerAppData
            if(peerAppData.position() != 0) {
                peerAppData.compact();
            }

            SSLEngineResult unwrapResult = sslEngine.unwrap(netInBuffer, peerAppData);

            switch (unwrapResult.getStatus()) {
                case OK: {
                    unwrapResult.bytesProduced();
                    if (unwrapResult.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) tasks();
                    break;
                }
                case BUFFER_OVERFLOW: {
                    int applicationBufferSize = sslEngine.getSession().getApplicationBufferSize();
                    if (applicationBufferSize > peerAppData.capacity()) {
                        int appSize = applicationBufferSize;
                        ByteBuffer b = ByteBuffer.allocate(appSize + peerAppData.position());
                        peerAppData.flip();
                        b.put(peerAppData);
                        peerAppData = b;
                    } else {
                        // We tried to unwrap data into peerAppData which means there's leftover in netInBuffer
                        // the upcoming read should read int potential new data after the leftover
                        netInBuffer.position(netInBuffer.limit());
                        netInBuffer.limit(netInBuffer.capacity());
                        peerAppData.compact();
                        if(!dst.hasRemaining()) {
                            return read;
                        }
                    }
                    break;
                }
                case BUFFER_UNDERFLOW: {
                    int packetBufferSize = sslEngine.getSession().getPacketBufferSize();
                    if (packetBufferSize > netInBuffer.capacity()) {
                        int netSize = packetBufferSize;
                        if (netSize > netInBuffer.capacity()) {
                            ByteBuffer b = ByteBuffer.allocate(netSize);
                            netInBuffer.flip();
                            b.put(netInBuffer);
                            netInBuffer = b;
                        }
                    } else {
                        // We have some leftover data from unwrap but no enough.
                        // We need to read in more data from the socket AFTER the current data.
                        netInBuffer.position(netInBuffer.limit());
                        netInBuffer.limit(netInBuffer.capacity());
                        channel.read(netInBuffer);
                        continue;
                    }
                    break;
                }
                default:
                    throw new IOException("Failed to SSL unwrap with status " + unwrapResult.getStatus());
            }

            if (peerAppData != dst && dst.hasRemaining()) {
                peerAppData.flip();
                read += copy(peerAppData, dst);
                if(!dst.hasRemaining()) {
                    netInBuffer.compact();
                    return read;
                }
            }

            netInBuffer.compact();
        }
        return read;
    }

    private int copy(ByteBuffer src, ByteBuffer dst) {
        int toCopy = Math.min(src.remaining(), dst.remaining());

        dst.put(src.array(), src.position(), toCopy);
        src.position(src.position() + toCopy);

        if(!src.hasRemaining()) {
            src.clear();
        }
        return toCopy;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if(closing || closed) {
            throw new IOException("Cannot perform socket write, the socket is closed (or being closed).");
        }

        int wrote = 0;
        // src can be much bigger than what our SSL session allows to send in one go
        while (src.hasRemaining()) {
            netOutBuffer.clear();

            SSLEngineResult wrapResult = sslEngine.wrap(src, netOutBuffer);
            netOutBuffer.flip();

            if (wrapResult.getStatus() == SSLEngineResult.Status.OK) {
                if (wrapResult.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) tasks();
            }

            while (netOutBuffer.hasRemaining()) {
                wrote += channel.write(netOutBuffer);
            }
        }

        return wrote;
    }

    // -----------------------------------------------------------
    // MISC
    // -----------------------------------------------------------

    private void tasks() {
        Runnable r;
        while ( (r = sslEngine.getDelegatedTask()) != null) {
            r.run();
        }
        hs = sslEngine.getHandshakeStatus();
    }

    public SocketChannel channel() {
        return channel;
    }

    SSLEngine getEngine() {
        return sslEngine;
    }

    boolean isHandshakeComplete() {
        return handshakeComplete;
    }
}
