package water.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class BinaryFileTransfer {

    /**
     * Hexadecimal string to brute-force convert into an array of bytes.
     * The length of the string must be even.
     * The length of the string is 2x the length of the byte array.
     *
     * @param s Hexadecimal string
     * @return byte array
     */
    public static byte[] convertStringToByteArr(String s) {
        if ((s.length() % 2) != 0) {
            throw new RuntimeException("String length must be even (was " + s.length() + ")");
        }

        ArrayList<Byte> byteArrayList = new ArrayList<Byte>();
        for (int i = 0; i < s.length(); i = i + 2) {
            String s2 = s.substring(i, i + 2);
            Integer i2 = Integer.parseInt(s2, 16);
            Byte b2 = (byte) (i2 & 0xff);
            byteArrayList.add(b2);
        }

        byte[] byteArr = new byte[byteArrayList.size()];
        for (int i = 0; i < byteArr.length; i++) {
            byteArr[i] = byteArrayList.get(i);
        }
        return byteArr;
    }

    public static void writeBinaryFile(String fileName, byte[] byteArr) throws IOException {
        FileOutputStream out = new FileOutputStream(fileName);
        for (byte b : byteArr) {
            out.write(b);
        }
        out.close();
    }

    /**
     * Array of bytes to brute-force convert into a hexadecimal string.
     * The length of the returned string is byteArr.length * 2.
     *
     * @param byteArr byte array to convert
     * @return hexadecimal string
     */
    public static String convertByteArrToString(byte[] byteArr) {
        StringBuilder sb = new StringBuilder();
        for (byte b : byteArr) {
            int i = b;
            i = i & 0xff;
            sb.append(String.format("%02x", i));
        }
        return sb.toString();
    }

}
