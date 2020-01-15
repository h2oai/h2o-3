package water;

import java.io.Closeable;
import java.io.InputStream;
import java.sql.Timestamp;

public final class ChunkAutoBufferReader implements Closeable  {
    
    private final AutoBuffer buffer;
    private final int numRows;
    private boolean isLastNA = false;
    
    public ChunkAutoBufferReader(InputStream inputStream) {
        this.buffer = new AutoBuffer(inputStream);
        this.numRows = readInt();
    }

    public int getNumRows() {
        return numRows;
    }

    public boolean readBoolean() {
        boolean data = buffer.getZ();
        isLastNA = ExternalFrameUtils.isNA(buffer, data);
        return data;
    }

    public byte readByte() {
        byte data = buffer.get1();
        isLastNA = ExternalFrameUtils.isNA(buffer, data);
        return data;
    }

    public char readChar() {
        char data = buffer.get2();
        isLastNA = ExternalFrameUtils.isNA(buffer, data);
        return data;
    }

    public short readShort() {
        short data = buffer.get2s();
        isLastNA = ExternalFrameUtils.isNA(buffer, data);
        return data;
    }

    public int readInt() {
        int data = buffer.getInt();
        isLastNA = ExternalFrameUtils.isNA(buffer, data);
        return data;
    }

    public long readLong() {
        long data = buffer.get8();
        isLastNA = ExternalFrameUtils.isNA(buffer, data);
        return data;
    }

    public float readFloat() {
        float data = buffer.get4f();
        isLastNA = ExternalFrameUtils.isNA(data);
        return data;
    }

    public double readDouble() {
        double data = buffer.get8d();
        isLastNA = ExternalFrameUtils.isNA(data);
        return data;
    }

    public String readString() {
        String data = buffer.getStr();
        isLastNA = ExternalFrameUtils.isNA(buffer, data);
        return data;
    }

    public Timestamp readTimestamp() {
        Timestamp data = new Timestamp(buffer.get8());
        isLastNA = ExternalFrameUtils.isNA(buffer, data);
        return data;
    }

    /**
     * This method is used to check if the last received value was marked as NA by H2O backend
     */
    public boolean isLastNA() {
        return isLastNA;
    }
    
    @Override
    public void close() {
        buffer.close();
    }
}
