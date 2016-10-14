package water;

import water.fvec.Chunk;
import water.fvec.ChunkUtils;
import water.fvec.Frame;
import water.parser.BufferedString;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.UUID;

/**
 * Use to read from H2O Frames from non H2O environments
 */
public class ExternalFrameReader {

    private static final byte IS_NA = 1;
    private static final byte NOT_NA = 0;

    // hints for expected types in order to handle download properly
    public static final byte EXPECTED_INT = 0;
    public static final byte EXPECTED_DOUBLE = 1;
    public static final byte EXPECTED_STRING = 2;

    private AutoBuffer ab;
    private String keyName;
    private byte[] expectedTypes;
    private int chunkIdx;
    private int[] selectedColumnIndices;
    private SocketChannel channel;

    private int numOfRows;
    public ExternalFrameReader(SocketChannel channel, String keyName, byte[] expectedTypes, int chunkIdx, int[] selectedColumnIndices) throws IOException{
        this.channel = channel;
        this.keyName = keyName;
        this.expectedTypes = expectedTypes;
        this.chunkIdx = chunkIdx;
        this.selectedColumnIndices = selectedColumnIndices;
        this.ab = prepareAutoBuffer();
        this.numOfRows = ab.getInt();
    }

    private AutoBuffer prepareAutoBuffer() throws IOException{
        AutoBuffer ab = new AutoBuffer();
        ab.put1(ExternalFrameHandler.INIT_BYTE);
        ab.putInt(ExternalFrameHandler.DOWNLOAD_FRAME);
        ab.putStr(keyName);
        ab.putA1(expectedTypes);
        ab.putInt(chunkIdx);
        ab.putA4(selectedColumnIndices);
        writeToChannel(ab, channel);
        return new AutoBuffer(channel, null);
    }

    public int getNumOfRows(){
        return numOfRows;
    }

    public long readLong(){
        return ab.get8();
    }

    public double readDouble(){
        return ab.get8d();
    }

    public String readString(){
        return ab.getStr();
    }

    public boolean readIsNA(){
        return ab.getInt() == IS_NA;
    }

    public void waitUntilAllReceived(){
        // confirm that all has been done before proceeding with the computation
        assert(ab.getInt() == ExternalFrameHandler.CONFIRM_READING_DONE);
    }

    static void handleReadingFromChunk(SocketChannel sock, AutoBuffer recvAb) throws IOException {
        String frame_key = recvAb.getStr();
        byte[] expectedTypes = recvAb.getA1();
        assert expectedTypes != null;
        int chunk_id = recvAb.getInt();
        int[] selectedColumnIndices = recvAb.getA4();
        assert selectedColumnIndices!=null;
        Frame fr = DKV.getGet(frame_key);
        Chunk[] chunks = ChunkUtils.getChunks(fr, chunk_id);

        AutoBuffer ab = new AutoBuffer().flipForReading().clearForWriting(H2O.MAX_PRIORITY);
        ab.putInt(chunks[0]._len); // num of rows
        writeToChannel(ab, sock);

        for (int rowIdx = 0; rowIdx < chunks[0]._len; rowIdx++) { // for each row
            for(int cidx: selectedColumnIndices){ // go through the chunks
                ab.flipForReading().clearForWriting(H2O.MAX_PRIORITY); // reuse existing ByteBuffer
                // write flag weather the row is na or not
                if (chunks[cidx].isNA(rowIdx)) {
                    ab.putInt(IS_NA);
                } else {
                    ab.putInt(NOT_NA);

                    Chunk chnk = chunks[cidx];
                    switch (expectedTypes[cidx]) {
                        case ExternalFrameReader.EXPECTED_INT:
                            if (chnk.vec().isNumeric() || chnk.vec().isTime()) {
                                ab.put8(chnk.at8(rowIdx));
                            } else {
                                assert chnk.vec().domain() != null && chnk.vec().domain().length != 0;
                                // in this case the chunk is categorical with integers in the domain
                                ab.put8(Integer.parseInt(chnk.vec().domain()[(int) chnk.at8(rowIdx)]));
                            }
                            break;
                        case ExternalFrameReader.EXPECTED_DOUBLE:
                            assert chnk.vec().isNumeric();
                            if (chnk.vec().isInt()) {
                                ab.put8(chnk.at8(rowIdx));
                            } else {
                                ab.put8d(chnk.atd(rowIdx));
                            }

                            break;
                        case ExternalFrameReader.EXPECTED_STRING:
                            assert chnk.vec().isCategorical() || chnk.vec().isString() || chnk.vec().isUUID();
                            ab.putStr(getStringFromChunk(chunks, cidx, rowIdx));
                            break;
                    }

                }
                writeToChannel(ab, sock);
            }
        }
        ab.flipForReading().clearForWriting(H2O.MAX_PRIORITY);
        ab.putInt(ExternalFrameHandler.CONFIRM_READING_DONE);
        writeToChannel(ab, sock);
    }



    private static void writeToChannel(AutoBuffer ab, SocketChannel channel) throws IOException {
        ab._bb.flip();
        while (ab._bb.hasRemaining()) {
            channel.write(ab._bb);
        }
    }

    private static String getStringFromChunk(Chunk[] chks, int columnNum, int rowIdx) {
        if (chks[columnNum].vec().isCategorical()) {
            return chks[columnNum].vec().domain()[(int) chks[columnNum].at8(rowIdx)];
        } else if (chks[columnNum].vec().isString()) {
            BufferedString valStr = new BufferedString();
            chks[columnNum].atStr(valStr, rowIdx); // TODO improve this.
            return valStr.toString();
        } else if (chks[columnNum].vec().isUUID()) {
            UUID uuid = new UUID(chks[columnNum].at16h(rowIdx), chks[columnNum].at16l(rowIdx));
            return uuid.toString();
        } else {

            assert false : "Null can never be returned at this point";
            return null;
        }
    }
}
