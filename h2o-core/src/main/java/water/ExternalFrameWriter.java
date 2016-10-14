package water;

import water.fvec.ChunkUtils;
import water.fvec.NewChunk;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.sql.Timestamp;

/**
 * Use to create H2O Frames from non H2O environments
 */
public class ExternalFrameWriter {

    private static final byte CREATE_NEW_CHUNK = 0;
    private static final byte ADD_TO_CHUNK = 1;
    private static final byte CLOSE_NEW_CHUNK = 2;

    private static final byte TYPE_NUM = 0;
    private static final byte TYPE_STR = 1;
    private static final byte TYPE_NA = 2;

    private AutoBuffer ab;
    private SocketChannel channel;

    public ExternalFrameWriter(SocketChannel channel){
        // using default constructor, AutoBuffer is created with
        // private property _read set to false, in order to satisfy call clearForWriting it has to be set to true
        // which does the call of flipForReading method
        ab = new AutoBuffer().flipForReading();
        this.channel = channel;
    }


    public void createChunks(String keystr, byte[] vecTypes, int chunkId) throws IOException {
        ab.clearForWriting(H2O.MAX_PRIORITY);
        ab.put1(ExternalFrameHandler.INIT_BYTE);
        ab.putInt(ExternalFrameHandler.CREATE_FRAME);
        ab.putInt(CREATE_NEW_CHUNK);
        ab.putStr(keystr);
        ab.putA1(vecTypes);
        ab.putInt(chunkId);
        writeToChannel(ab, channel);
    }

    public void closeChunks() throws IOException{
        ab.clearForWriting(H2O.MAX_PRIORITY);
        ab.putInt(CLOSE_NEW_CHUNK);
        writeToChannel(ab, channel);

        AutoBuffer confirmAb = new AutoBuffer(channel, null);
        // this needs to be here because confirmAb.getInt() forces this code to wait for result and
        // all the previous work to be done on the recipient side. The assert around it is just additional, not
        // so important check
        assert(confirmAb.getInt() == ExternalFrameHandler.CONFIRM_WRITING_DONE);
    }

    private void setPutHeader(){
        ab.clearForWriting(H2O.MAX_PRIORITY);
        ab.putInt(ADD_TO_CHUNK);
    }

    public void put(int columnNum, double num) throws IOException{
        setPutHeader();
        ab.putInt(TYPE_NUM);
        ab.putInt(columnNum);
        ab.put8d(num);
        writeToChannel(ab, channel);
    }


    public void put(int columnNum, boolean b) throws IOException{
        setPutHeader();
        ab.putInt(TYPE_NUM);
        ab.putInt(columnNum);
        ab.put8d(b ? 1 : 0);
        writeToChannel(ab, channel);
    }

    public void put(int columnNum, Timestamp timestamp) throws IOException{
        setPutHeader();
        ab.putInt(TYPE_NUM);
        ab.putInt(columnNum);
        ab.put8d(timestamp.getTime());
        writeToChannel(ab, channel);
    }

    public void put(int columnNum, String str) throws IOException{
        setPutHeader();
        ab.putInt(TYPE_STR);
        ab.putInt(columnNum);
        ab.putStr(str);
        writeToChannel(ab, channel);
    }

    public void putNA(int columnNum) throws IOException{
        setPutHeader();
        ab.putInt(TYPE_NA);
        ab.putInt(columnNum);
        writeToChannel(ab, channel);
    }

    static void handleWriteToChunk(SocketChannel sock, AutoBuffer ab) throws IOException {
        NewChunk[] nchnk = null;
        int requestType;
        do {
            requestType = ab.getInt();
            switch (requestType) {
                case CREATE_NEW_CHUNK:
                    String frame_key = ab.getStr();
                    byte[] vec_types = ab.getA1();
                    int chunk_id = ab.getInt();
                    nchnk = ChunkUtils.createNewChunks(frame_key, vec_types, chunk_id);
                    break;
                case ADD_TO_CHUNK:
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
                case CLOSE_NEW_CHUNK:
                    ChunkUtils.closeNewChunks(nchnk);

                    AutoBuffer outputAb = new AutoBuffer().flipForReading().clearForWriting(H2O.MAX_PRIORITY);
                    // flag informing sender that all work is done and
                    // chunks are ready to be finalized.
                    //
                    // This also needs to be sent because in the sender we have to
                    // wait for all chunks to be written to DKV; otherwise we get race during finalizing and
                    // it happens that we try to finalize frame with chunks not ready yet
                    outputAb.putInt(ExternalFrameHandler.CONFIRM_WRITING_DONE);
                    writeToChannel(outputAb, sock);
                    break;
            }
        } while (requestType != CLOSE_NEW_CHUNK);
    }

    private static void writeToChannel(AutoBuffer ab, SocketChannel channel) throws IOException {
        ab.flipForReading();
        while (ab._bb.hasRemaining()){
            channel.write(ab._bb);
        }
    }
}
