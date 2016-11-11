package water;

import water.fvec.Vec;

import javax.print.DocFlavor;
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

    /**
     * Get connection to a specific h2o node. The caller of this method is usually non-H2O node who wants to read H2O
     * frames or write to H2O frames from non-H2O environment, such as Spark executor.
     */
    public static ByteChannel getConnection(String h2oNodeHostname, int h2oNodeApiPort) throws IOException{
        return H2ONode.openChan(TCPReceiverThread.TCP_EXTERNAL, null, h2oNodeHostname, h2oNodeApiPort +1);

    }

    public static ByteChannel getConnection(String ipPort) throws IOException{
        String[] split = ipPort.split(":");
        return getConnection(split[0], Integer.parseInt(split[1]));
    }

    public static byte[] vecTypesFromExpectedTypes(byte[] expectedTypes){
        byte[] vecTypes = new byte[expectedTypes.length];
        for (int i = 0; i < expectedTypes.length; i++) {
            switch (expectedTypes[i]){
                case EXPECTED_BOOL: vecTypes[i] = Vec.T_NUM; break;
                case EXPECTED_BYTE: vecTypes[i] = Vec.T_NUM; break;
                case EXPECTED_CHAR: vecTypes[i] = Vec.T_NUM; break;
                case EXPECTED_SHORT: vecTypes[i] = Vec.T_NUM; break;
                case EXPECTED_INT: vecTypes[i] = Vec.T_NUM; break;
                case EXPECTED_LONG: vecTypes[i] = Vec.T_NUM; break;
                case EXPECTED_FLOAT: vecTypes[i] = Vec.T_NUM; break;
                case EXPECTED_DOUBLE: vecTypes[i] = Vec.T_NUM; break;
                case EXPECTED_STRING: vecTypes[i] = Vec.T_STR; break;
                case EXPECTED_TIMESTAMP: vecTypes[i] = Vec.T_TIME; break;
                default: throw new IllegalArgumentException("Unknown expected type: "+expectedTypes[i]);
            }
        }
        return vecTypes;
    }

    public static byte[] prepareExpectedTypes(Class[] javaClasses){
        byte[] expectedTypes = new byte[javaClasses.length];
        for (int i = 0; i < javaClasses.length; i++) {
            Class clazz = javaClasses[i];
            if(clazz == Boolean.class){
                expectedTypes[i] = EXPECTED_BOOL;
            } else if(clazz == Byte.class){
                expectedTypes[i] = EXPECTED_BYTE;
            }else if(clazz == Short.class){
                expectedTypes[i] = EXPECTED_SHORT;
            }else if(clazz == Character.class){
                expectedTypes[i] = EXPECTED_CHAR;
            }else if(clazz == Integer.class){
                expectedTypes[i] = EXPECTED_INT;
            }else if(clazz == Long.class){
                expectedTypes[i] = EXPECTED_LONG;
            }else if(clazz == Float.class){
                expectedTypes[i] = EXPECTED_FLOAT;
            }else if(clazz == Double.class){
                expectedTypes[i] = EXPECTED_DOUBLE;
            }else if(clazz == String.class){
                expectedTypes[i] = EXPECTED_STRING;
            }else if(clazz == Timestamp.class){
                expectedTypes[i] = EXPECTED_TIMESTAMP;
            }else{
                throw new IllegalArgumentException("Unknown java class " + clazz);
            }
        }
        return expectedTypes;
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
