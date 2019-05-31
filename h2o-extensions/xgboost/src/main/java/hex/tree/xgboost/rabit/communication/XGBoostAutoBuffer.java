package hex.tree.xgboost.rabit.communication;

import water.AutoBuffer;
import water.util.StringUtils;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

// Wrapper class for communication with XGBoost cluster. XGBoost cluster does not use int compression
// when sending/receiving string length. Other basic functionality seems to be the same.
public class XGBoostAutoBuffer {

    private AutoBuffer ab;

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    public XGBoostAutoBuffer(SocketChannel channel) throws IOException {
        this.ab = new AutoBuffer(channel);
    }

    public XGBoostAutoBuffer() {
        this.ab = new AutoBuffer();
    }

    // Used to communicate with external frameworks, for example XGBoost
    public String getStr( ) {
        int len = ab.get4();
        return len == -1 ? null : new String(ab.getA1(len), UTF_8);
    }

    // Used to communicate with external frameworks, for example XGBoost
    public XGBoostAutoBuffer putStr(String s ) {
        ab.put4(s.length());
        byte[] a = StringUtils.bytesOf(s);
        ab.putA1(a, a.length);
        return this;
    }

    public int get4() {
        return ab.get4();
    }

    public void put4(int number) {
        ab.put4(number);
    }

    public AutoBuffer buffer() {
        return ab;
    }
}
