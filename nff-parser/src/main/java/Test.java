import java.nio.ByteBuffer;
import java.util.Arrays;

public class Test {

    public static void main(String args[]){

        byte[] bytes = new byte[8];
        ByteBuffer.wrap(bytes).putDouble(1D);
        System.out.println("Result: " + Arrays.toString(bytes));

        double l = ByteBuffer.wrap(bytes).getDouble();
        System.out.println("ppp:"+l);

//        byte[] bytes = ByteBuffer.allocate(4).putInt(-4).array();
////        for (byte b : bytes) {
////            System.out.format("0x%x ", b);
////            System.out.println(b);
////        }
//        System.out.println(bytes.length);
//
//        String s1 = String.format("%8s", Integer.toBinaryString(bytes[3] & 0xFF)).replace(' ', '0');
//        System.out.println(s1);
//        System.out.println(bytes[0]);
//        System.out.println(bytes[1]);
//        System.out.println(bytes[2]);
//        System.out.println(bytes[3]);
    }
}
