package water;


import water.fvec.Chunk;
import water.fvec.ChunkUtils;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.parser.BufferedString;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.UUID;

/**
 * Add chunks and data to non-finalized frame from non-h2o environment (ie. Spark executors)
 */
public class ExternalFrameHandler {

    public static final byte INIT_BYTE = 42;
    // main tasks
    public static final byte CREATE_FRAME = 0;
    public static final byte DOWNLOAD_FRAME = 1;

    // subtaks for task CREATE_FRAME
    public static final byte CREATE_NEW_CHUNK = 0;
    public static final byte ADD_TO_CHUNK = 1;
    public static final byte CLOSE_NEW_CHUNK = 2;

    public static final byte TYPE_NUM = 0;
    public static final byte TYPE_STR = 1;
    public static final byte TYPE_NA = 2;

    // hints for expected types in order to handle download properly
    public static final byte T_INTEGER = 0;
    public static final byte T_DOUBLE = 1;
    public static final byte T_STRING = 2;

    void process(SocketChannel sock, AutoBuffer ab) throws IOException {
        int requestType = ab.getInt();
        switch (requestType) {
            case CREATE_FRAME:
                handleUploadChunk(sock, ab);
                break;
            case DOWNLOAD_FRAME:
                handleDownloadChunk(sock, ab);
                break;
        }
    }

    private void handleDownloadChunk(SocketChannel sock, AutoBuffer recvAb) throws IOException {
        String frame_key = recvAb.getStr();
        byte[] expectedTypes = recvAb.getA1();
        assert expectedTypes != null;
        int chunk_id = recvAb.getInt();
        Frame fr = DKV.getGet(frame_key);
        Chunk[] chunks = ChunkUtils.getChunks(fr, chunk_id);

        AutoBuffer ab = new AutoBuffer().flipForReading().clearForWriting(H2O.MAX_PRIORITY);
        ab.putInt(chunks[0]._len); // num of rows
        writeToChannel(ab, sock);

        for (int rowIdx = 0; rowIdx < chunks[0]._len; rowIdx++) { // for each row
            for (int cidx = 0; cidx < chunks.length; cidx++) { // go through the chunks
                ab.clearForWriting(H2O.MAX_PRIORITY); // reuse existing ByteBuffer
                // write flag weather the row is na or not
                if (chunks[cidx].isNA(rowIdx)) {
                    ab.putInt(1);
                } else {
                    ab.putInt(0);

                    Chunk chnk = chunks[cidx];
                    switch (expectedTypes[cidx]) {
                        case T_INTEGER:
                            if (chnk.vec().isNumeric() || chnk.vec().isTime()) {
                                ab.put8(chnk.at8(rowIdx));
                            } else {
                                assert chnk.vec().domain() != null && chnk.vec().domain().length != 0;
                                // in this case the chunk is categorical with integers in the domain
                                ab.put8(Integer.parseInt(chnk.vec().domain()[(int) chnk.at8(rowIdx)]));
                            }
                            break;
                        case T_DOUBLE:
                            assert chnk.vec().isNumeric();
                            if (chnk.vec().isInt()) {
                                ab.put8(chnk.at8(rowIdx));
                            } else {
                                ab.put8d(chnk.atd(rowIdx));
                            }

                            break;
                        case T_STRING:
                            assert chnk.vec().isCategorical() || chnk.vec().isString() || chnk.vec().isUUID();
                            ab.putStr(getStringFromChunk(chunks, cidx, rowIdx));
                            break;
                    }

                }
                writeToChannel(ab, sock);
            }
        }
    }

    private void handleUploadChunk(SocketChannel sock, AutoBuffer ab) throws IOException {
        NewChunk[] nchnk = null;
        int requestType;
        do {
            requestType = ab.getInt();
            switch (requestType) {
                case CREATE_NEW_CHUNK: // Create new chunks
                    String frame_key = ab.getStr();
                    byte[] vec_types = ab.getA1();
                    int chunk_id = ab.getInt();
                    nchnk = ChunkUtils.createNewChunks(frame_key, vec_types, chunk_id);
                    break;
                case ADD_TO_CHUNK: // Add to existing frame
                    int dataType = ab.getInt();
                    int colNum = ab.getInt();
                    assert nchnk != null;
                    switch (dataType) {
                        case TYPE_NA:
                            nchnk[colNum].addNA();
                            break;
                        case TYPE_NUM:
                            double d = ab.get8d();
                            nchnk[colNum].addNum(d);
                            break;
                        case TYPE_STR:
                            String str = ab.getStr();
                            // Helper to hold H2O string
                            nchnk[colNum].addStr(str);
                            break;
                    }
                    break;
                case CLOSE_NEW_CHUNK: // Close new chunks
                    ChunkUtils.closeNewChunks(nchnk);
                    AutoBuffer outputAb = new AutoBuffer().flipForReading().clearForWriting(H2O.MAX_PRIORITY);
                    // flag informing sender that all work is done and
                    // chunks are ready to be finalized. This needs to be sent because it in the sender we have to
                    // wait for all chunks to be written to DKV; otherwise  we get race during finalizing and
                    // it happens that we try to finalize frame with chunks not ready yet
                    outputAb.put1(1);
                    writeToChannel(outputAb, sock);
                    break;
            }
        } while (requestType != CLOSE_NEW_CHUNK);
    }

    private void writeToChannel(AutoBuffer ab, SocketChannel channel) throws IOException {
        ab._bb.flip();
        while (ab._bb.hasRemaining()) {
            channel.write(ab._bb);
        }
    }

    private String getStringFromChunk(Chunk[] chks, int columnNum, int rowIdx) {
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
