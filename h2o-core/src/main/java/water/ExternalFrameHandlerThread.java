package water;

import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * <p>This class is a thread which exists per each new connection of type {@code TCPReceiverThread.TCP_EXTERNAL}</p>
 *
 * <p>It is started for the connection and waits for the {@code INIT_BYTE}. If the {@code INIT_BYTE} has been received,
 * the socket channel and corresponding {@link AutoBuffer} is sent to {@link ExternalFrameHandler} to handle the
 * particular request.</p>
 *
 * <p>The {@code INIT_BYTE} has to be sent since the connection can be reused on the caller side and we need to know
 * that new bunch of requests or data is coming.</p>
 */
class ExternalFrameHandlerThread extends Thread {
    private SocketChannel _sock;
    private AutoBuffer _ab;

    ExternalFrameHandlerThread(SocketChannel sock, AutoBuffer ab) {
        super("TCP-"+sock);
        _sock = sock;
        _ab = ab;
        setPriority(MAX_PRIORITY-1);
    }

    @Override
    public void run() {
        while (true) { // Loop, reading fresh TCP requests until the sender closes

            try {
                // blocking call
                if(_ab.get1() != ExternalFrameHandler.INIT_BYTE){
                    // check whether this channel contains data for next frame task
                    // otherwise close the connection here
                    throw new IOException("This connection is idle, expected data are not available");
                }
                new ExternalFrameHandler().process(_sock, _ab);
                // Reuse open sockets for the next task
                if (!_sock.isOpen()) break;
                _ab = new AutoBuffer(_sock, null);
            } catch (Exception e) {
                // Exceptions here are *normal*, this is an idle TCP connection and
                // either the OS can time it out, or the cloud might shutdown.  We
                // don't care what happens to this socket.
                break;         // Ignore all errors; silently die if socket is closed
            }
        }

    }
}

