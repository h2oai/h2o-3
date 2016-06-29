package water;

import water.util.Log;

import java.io.IOException;
import java.nio.channels.SocketChannel;

class ExternalFrameHandlerThread extends Thread {
    private SocketChannel _sock;
    private AutoBuffer _ab;

    public ExternalFrameHandlerThread(SocketChannel sock, AutoBuffer ab) {
        super("TCP-"+sock);
        _sock = sock;
        _ab = ab;
        setPriority(MAX_PRIORITY-1);
    }

    @Override
    public void run() {
        while (true) { // Loop, reading fresh TCP requests until the sender closes

            try {
                // try waiting for one second
                if(_ab.get1() != ExternalFrameHandler.INIT_BYTE){
                    // check whether this channel contains data for next frame task
                    // otherwise close the connection here
                    throw new IOException("This connection is idle, expected data are not available");
                }
                new ExternalFrameHandler().process(_sock, _ab);
                // Reuse open sockets for the next task
                if (!_sock.isOpen()) break;
                _ab = new AutoBuffer(_sock, false);
            } catch (Exception e) {
                // Exceptions here are *normal*, this is an idle TCP connection and
                // either the OS can time it out, or the cloud might shutdown.  We
                // don't care what happens to this socket.
                break;         // Ignore all errors; silently die if socket is closed
            }
        }

    }
}

