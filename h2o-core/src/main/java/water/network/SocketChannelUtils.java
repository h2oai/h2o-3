package water.network;

import java.nio.channels.ByteChannel;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;

public class SocketChannelUtils {

    public static boolean isSocketChannel(Channel channel) {
        return channel instanceof SocketChannel || channel instanceof SSLSocketChannel;
    }

    public static SocketChannel underlyingSocketChannel(Channel channel) {
        if(channel instanceof SSLSocketChannel) {
            return ((SSLSocketChannel) channel).channel();
        } else if(channel instanceof SocketChannel) {
            return (SocketChannel) channel;
        }
        throw new UnsupportedOperationException(
                "Channel is not a socket channel. Cannot retrieve the underlying channel."
        );
    }

}
