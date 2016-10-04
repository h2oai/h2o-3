package water.network;

import org.junit.Test;
import water.TestUtil;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import static org.junit.Assert.*;

public class SSLSocketChannelFactoryTest {

    private int port = 9999;

    @Test
    public void shouldHandshake() throws IOException, SSLContextException, BrokenBarrierException, InterruptedException {
        SSLProperties props = new SSLProperties();
        props.put("h2o_ssl_protocol", SecurityUtils.defaultTLSVersion());
        props.put("h2o_ssl_jks_internal", TestUtil.find_test_file_static("src/test/resources/keystore.jks").getPath());
        props.put("h2o_ssl_jks_password", "password");
        props.put("h2o_ssl_jts", TestUtil.find_test_file_static("src/test/resources/cacerts.jks").getPath());
        props.put("h2o_ssl_jts_password", "password");

        final SSLSocketChannelFactory factory = new SSLSocketChannelFactory(props);

        final CyclicBarrier barrier = new CyclicBarrier(2);
        final CyclicBarrier testOne = new CyclicBarrier(2);
        final CyclicBarrier testTwo = new CyclicBarrier(2);
        final CyclicBarrier testThree = new CyclicBarrier(2);

        final boolean[] hs = new boolean[]{true};

        Thread client = new ClientThread(factory, testOne, testTwo, testThree, barrier);
        client.setDaemon(false);
        client.start();

        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().setReceiveBufferSize(64 * 1024);
            while(true) {
                try {
                    serverSocketChannel.socket().bind(new InetSocketAddress(port));
                    break;
                } catch (BindException e) {
                    port++;
                }
            }
            barrier.await();
            SocketChannel sock = serverSocketChannel.accept();
            barrier.reset();

            SSLSocketChannel wrappedChannel = (SSLSocketChannel) factory.wrapServerChannel(sock);

            assertTrue(wrappedChannel.isHandshakeComplete());

            // FIRST TEST: SSL -> SSL SMALL COMMUNICATION
            ByteBuffer readBuffer = ByteBuffer.allocate(12);

            while (readBuffer.hasRemaining()) {
                wrappedChannel.read(readBuffer);
            }

            readBuffer.flip();

            byte[] dst = new byte[12];
            readBuffer.get(dst, 0, 12);
            readBuffer.clear();

            assertEquals("hello, world", new String(dst, "UTF-8"));
            testOne.await();

            // SECOND TEST: SSL -> SSL BIG COMMUNICATION
            int read = 0;
            byte[] dstBig = new byte[16];
            ByteBuffer readBufferBig = ByteBuffer.allocate(1024);
            while (read < 5 * 64 * 1024) {
                while (readBufferBig.position() < 16) {
                    wrappedChannel.read(readBufferBig);
                }

                readBufferBig.flip();
                readBufferBig.get(dstBig, 0, 16);
                if (!readBufferBig.hasRemaining()) {
                    readBufferBig.clear();
                } else {
                    readBufferBig.compact();
                }
                assertEquals("hello, world" + (read % 9) + "!!!", new String(dstBig, "UTF-8"));
                read += 16;
            }

            testTwo.await();

            // THIRD TEST: NON-SSL -> SSL COMMUNICATION
            try {
                while (readBuffer.hasRemaining()) {
                    wrappedChannel.read(readBuffer);
                }
                fail();
            } catch (SSLException e) {
                // PASSED
            }

            assertTrue(wrappedChannel.getEngine().isInboundDone());

            testThree.await();

            // FOURTH TEST: SSL -> NON-SSL COMMUNICATION
            readBuffer.clear();
            while (readBuffer.hasRemaining()) {
                sock.read(readBuffer);
            }

            readBuffer.flip();
            readBuffer.get(dst, 0, 12);
            readBuffer.clear();

            assertNotEquals("hello, world", new String(dst, "UTF-8"));
        } catch (IOException | InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }

        barrier.await();
        assertTrue("One of the handshakes failed!", hs[0]);
    }


    private class ClientThread extends Thread {
        private final SSLSocketChannelFactory factory;
        private final CyclicBarrier testOne;
        private final CyclicBarrier testTwo;
        private final CyclicBarrier testThree;
        private final CyclicBarrier barrier;

        public ClientThread(SSLSocketChannelFactory factory,
                            CyclicBarrier testOne,
                            CyclicBarrier testTwo,
                            CyclicBarrier testThree,
                            CyclicBarrier barrier) {
            this.factory = factory;
            this.testOne = testOne;
            this.testTwo = testTwo;
            this.testThree = testThree;
            this.barrier = barrier;
        }

        @Override
        public void run() {
            try {
                barrier.await();
                SocketChannel sock = SocketChannel.open();
                sock.socket().setReuseAddress(true);
                sock.socket().setSendBufferSize(64 * 1024);
                InetSocketAddress isa = new InetSocketAddress("127.0.0.1", port);
                sock.connect(isa);
                sock.configureBlocking(true);
                sock.socket().setTcpNoDelay(true);

                SSLSocketChannel wrappedChannel = (SSLSocketChannel) factory.wrapClientChannel(sock, "127.0.0.1", port);

                // FIRST TEST: SSL -> SSL SMALL COMMUNICATION
                ByteBuffer write = ByteBuffer.allocate(1024);
                write.put("hello, world".getBytes("UTF-8"));
                write.flip();
                wrappedChannel.write(write);

                testOne.await();

                // SECOND TEST: SSL -> SSL BIG COMMUNICATION
                ByteBuffer toWriteBig = ByteBuffer.allocate(64 * 1024);
                for (int i = 0; i < 5; i++) {
                    toWriteBig.clear();
                    while (toWriteBig.hasRemaining()) {
                        toWriteBig.put(
                                ("hello, world" + ((i * 64 * 1024 + toWriteBig.position()) % 9) + "!!!")
                                        .getBytes("UTF-8")
                        );
                    }
                    toWriteBig.flip();
                    wrappedChannel.write(toWriteBig);
                }

                testTwo.await();

                // THIRD TEST: NON-SSL -> SSL COMMUNICATION
                write.clear();
                write.put("hello, world".getBytes("UTF-8"));
                write.flip();
                sock.write(write);

                testThree.await();

                // FOURTH TEST: SSL -> NON-SSL COMMUNICATION
                write.clear();
                write.put("hello, world".getBytes("UTF-8"));
                write.flip();
                wrappedChannel.write(write);

            } catch (IOException | InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            } finally {
                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}