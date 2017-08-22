package water;

import water.fvec.Vec;
import water.network.SocketChannelFactory;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.sql.Timestamp;

import static water.ExternalFrameHandler.*;

/**
 * Various utilities methods to help with external frame handling.
 */
public class ExternalFrameUtils {

    /**
     * Hints for expected types in order to improve performance network communication.
     * We make use of these to send and receive data we actually have.
     *
     * Each supported type for conversion has to be specified here
     */
    public static final byte EXPECTED_BOOL = 0;
    public static final byte EXPECTED_BYTE = 1;
    public static final byte EXPECTED_CHAR = 2;
    public static final byte EXPECTED_SHORT = 3;
    public static final byte EXPECTED_INT = 4;
    public static final byte EXPECTED_FLOAT = 5;
    public static final byte EXPECTED_LONG = 6;
    public static final byte EXPECTED_DOUBLE = 7;
    public static final byte EXPECTED_STRING = 8;
    public static final byte EXPECTED_TIMESTAMP = 9;
    public static final byte EXPECTED_VECTOR = 10;

    /**
     * Meta Information used to specify whether we should expect sparse or dense vector
     */
    public static final boolean VECTOR_IS_SPARSE = true;
    public static final boolean VECTOR_IS_DENSE = false;

    /**
     * Get connection to a specific h2o node. The caller of this method is usually non-H2O node who wants to read H2O
     * frames or write to H2O frames from non-H2O environment, such as Spark executor.
     */
    public static ByteChannel getConnection(String h2oNodeHostname, int h2oNodeApiPort) throws IOException{
        SocketChannelFactory socketFactory = SocketChannelFactory.instance(H2OSecurityManager.instance());
        return H2ONode.openChan(TCPReceiverThread.TCP_EXTERNAL, socketFactory, h2oNodeHostname, h2oNodeApiPort +1);
    }

    public static ByteChannel getConnection(String ipPort) throws IOException{
        String[] split = ipPort.split(":");
        return getConnection(split[0], Integer.parseInt(split[1]));
    }

    public static byte[] vecTypesFromExpectedTypes(byte[] expectedTypes, int[] vecElemSizes){
        int size = expectedTypes.length;
        if(vecElemSizes != null && vecElemSizes.length > 0){
            size = size - vecElemSizes.length;
            // length is number of simple expected types
            // plus length of all vectors. the expected
            for(int vecSize: vecElemSizes){
                size += vecSize;
            }
        }

        byte[] vecTypes = new byte[size];
        int vectorCount = 0;
        int currentVecIdx = 0;
        for (byte expectedType: expectedTypes) {
            switch (expectedType) {
                case EXPECTED_BOOL:
                    vecTypes[currentVecIdx] = Vec.T_NUM;
                    currentVecIdx++;
                    break;
                case EXPECTED_BYTE:
                    vecTypes[currentVecIdx] = Vec.T_NUM;
                    currentVecIdx++;
                    break;
                case EXPECTED_CHAR:
                    vecTypes[currentVecIdx] = Vec.T_NUM;
                    currentVecIdx++;
                    break;
                case EXPECTED_SHORT:
                    vecTypes[currentVecIdx] = Vec.T_NUM;
                    currentVecIdx++;
                    break;
                case EXPECTED_INT:
                    vecTypes[currentVecIdx] = Vec.T_NUM;
                    currentVecIdx++;
                    break;
                case EXPECTED_LONG:
                    vecTypes[currentVecIdx] = Vec.T_NUM;
                    currentVecIdx++;
                    break;
                case EXPECTED_FLOAT:
                    vecTypes[currentVecIdx] = Vec.T_NUM;
                    currentVecIdx++;
                    break;
                case EXPECTED_DOUBLE:
                    vecTypes[currentVecIdx] = Vec.T_NUM;
                    currentVecIdx++;
                    break;
                case EXPECTED_STRING:
                    vecTypes[currentVecIdx] = Vec.T_STR;
                    currentVecIdx++;
                    break;
                case EXPECTED_TIMESTAMP:
                    vecTypes[currentVecIdx] = Vec.T_TIME;
                    currentVecIdx++;
                    break;
                case EXPECTED_VECTOR:
                    for (int j = 0; j < vecElemSizes[vectorCount]; j++) {
                        vecTypes[currentVecIdx] = Vec.T_NUM;
                        currentVecIdx++;
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown expected type: " + expectedType);
            }
        }
       return vecTypes;

    }

    static void sendIntArray(AutoBuffer ab, ByteChannel channel, int[] data) throws IOException{
        ab.putA4(data);
        writeToChannel(ab, channel);
    }

    static void sendDoubleArray(AutoBuffer ab, ByteChannel channel, double[] data) throws IOException{
        ab.putA8d(data);
        writeToChannel(ab, channel);
    }

    static void sendBoolean(AutoBuffer ab, ByteChannel channel, boolean data) throws IOException{
        sendBoolean(ab, channel, data ? (byte)1 : (byte)0);
    }

    static void sendBoolean(AutoBuffer ab, ByteChannel channel, byte boolData) throws IOException{
        ab.put1(boolData);
        putMarkerAndSend(ab, channel, boolData);
    }

    static void sendByte(AutoBuffer ab, ByteChannel channel, byte data) throws IOException{
        ab.put1(data);
        putMarkerAndSend(ab, channel, data);
    }

    static void sendChar(AutoBuffer ab, ByteChannel channel, char data) throws IOException{
        ab.put2(data);
        putMarkerAndSend(ab, channel, data);
    }

    static void sendShort(AutoBuffer ab, ByteChannel channel, short data) throws IOException{
        ab.put2s(data);
        putMarkerAndSend(ab, channel, data);
    }

    static void sendInt(AutoBuffer ab, ByteChannel channel, int data) throws IOException{
        ab.putInt(data);
        putMarkerAndSend(ab, channel, data);
    }

    static void sendLong(AutoBuffer ab, ByteChannel channel, long data) throws IOException{
        ab.put8(data);
        putMarkerAndSend(ab, channel, data);
    }

    static void sendFloat(AutoBuffer ab, ByteChannel channel, float data) throws IOException{
        ab.put4f(data);
        writeToChannel(ab, channel);
    }

    static void sendDouble(AutoBuffer ab, ByteChannel channel, double data) throws IOException{
        ab.put8d(data);
        writeToChannel(ab, channel);
    }

    static void sendString(AutoBuffer ab, ByteChannel channel, String data) throws IOException{
        ab.putStr(data);
        if(data != null && data.equals(STR_MARKER_NEXT_BYTE_FOLLOWS)){
            ab.put1(MARKER_ORIGINAL_VALUE);
        }
        writeToChannel(ab, channel);
    }

    static void sendTimestamp(AutoBuffer ab, ByteChannel channel, long time) throws IOException{
        sendLong(ab, channel, time);
    }

    static void sendTimestamp(AutoBuffer ab, ByteChannel channel, Timestamp data) throws IOException{
        sendLong(ab, channel, data.getTime());
    }

    static void sendNA(AutoBuffer ab, ByteChannel channel, byte expectedType) throws IOException{
        switch (expectedType){
            case EXPECTED_BOOL: // // fall through to byte since BOOL is internally stored in frame as number (byte)
            case EXPECTED_BYTE:
                ab.put1(NUM_MARKER_NEXT_BYTE_FOLLOWS);
                ab.put1(MARKER_NA);
                writeToChannel(ab, channel);
                break;
            case EXPECTED_CHAR:
                ab.put2(NUM_MARKER_NEXT_BYTE_FOLLOWS);
                ab.put1(MARKER_NA);
                writeToChannel(ab, channel);
                break;
            case EXPECTED_SHORT:
                ab.put2s(NUM_MARKER_NEXT_BYTE_FOLLOWS);
                ab.put1(MARKER_NA);
                writeToChannel(ab, channel);
                break;
            case EXPECTED_INT:
                ab.putInt(NUM_MARKER_NEXT_BYTE_FOLLOWS);
                ab.put1(MARKER_NA);
                writeToChannel(ab, channel);
                break;
            case EXPECTED_TIMESTAMP: // fall through to long since TIMESTAMP is internally stored in frame as long
            case EXPECTED_LONG:
                ab.put8(NUM_MARKER_NEXT_BYTE_FOLLOWS);
                ab.put1(MARKER_NA);
                writeToChannel(ab, channel);
                break;
            case EXPECTED_FLOAT:
                ab.put4f(Float.NaN);
                writeToChannel(ab, channel);
                break;
            case EXPECTED_DOUBLE:
                ab.put8d(Double.NaN);
                writeToChannel(ab, channel);
                break;
            case EXPECTED_STRING:
                ab.putStr(STR_MARKER_NEXT_BYTE_FOLLOWS);
                ab.put1(MARKER_NA);
                writeToChannel(ab, channel);
                break;
            default:
                throw new IllegalArgumentException("Unknown expected type " + expectedType);
        }
    }

    public static boolean isNA(AutoBuffer ab, boolean data){
        return isNA(ab, data ? (long) 1: 0);
    }

    public static boolean isNA(AutoBuffer ab, long data){
        return data == NUM_MARKER_NEXT_BYTE_FOLLOWS && ab.get1() == MARKER_NA;
    }

    public static boolean isNA(double data){
        return Double.isNaN(data);
    }

    public static boolean isNA(AutoBuffer ab, Timestamp data){
        return isNA(ab, data.getTime());
    }

    public static boolean isNA(AutoBuffer ab, String data){
        return data != null && data.equals(STR_MARKER_NEXT_BYTE_FOLLOWS) && ab.get1() == MARKER_NA;
    }

    static int[] getStartPositions(int[] elemSizes){
        int[] startPos = new int[elemSizes.length];
        for(int i = 1; i<elemSizes.length; i++){
            startPos[i] = startPos[ i - 1] + elemSizes[i - 1];
        }
        return startPos;
    }

    static int[] getElemSizes(byte[] expectedTypes, int[] vecElemSizes){
        int vecCount = 0;
        int[] elemSizes = new int[expectedTypes.length];
        for(int i = 0; i<expectedTypes.length; i++){
            switch (expectedTypes[i]){
                case EXPECTED_BOOL:
                case EXPECTED_BYTE:
                case EXPECTED_CHAR:
                case EXPECTED_SHORT:
                case EXPECTED_INT:
                case EXPECTED_LONG:
                case EXPECTED_FLOAT:
                case EXPECTED_DOUBLE:
                case EXPECTED_STRING:
                case EXPECTED_TIMESTAMP: elemSizes[i] = 1; break;
                case EXPECTED_VECTOR: elemSizes[i] = vecElemSizes[vecCount]; break;
            }
        }
        return elemSizes;
    }

    /**
     * Sends another byte as a marker if it's needed and send the data
     */
    private static void putMarkerAndSend(AutoBuffer ab, ByteChannel channel, long data) throws IOException{
        if(data == NUM_MARKER_NEXT_BYTE_FOLLOWS){
            // we need to send another byte because zero is represented as 00 ( 2 bytes )
            ab.put1(MARKER_ORIGINAL_VALUE);
        }
        writeToChannel(ab, channel);
    }

    static void writeToChannel(AutoBuffer ab, ByteChannel channel) throws IOException {
        ab.flipForReading();
        channel.write(ab._bb);
        ab.clearForWriting(H2O.MAX_PRIORITY);
    }
}
