package water;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.*;

public class TypeMapTest extends TestUtil {

    @BeforeClass()
    public static void setup() { stall_till_cloudsize(1); }
    
    @Test
    public void testPrintTypeMap() {
        TypeMap.printTypeMap(System.out);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(os)) {
            TypeMap.printTypeMap(ps);
        }
        String output = os.toString();
        String[] lines = output.split("\n");
        String[] bootstrapClasses = TypeMap.bootstrapClasses();
        assertTrue(bootstrapClasses.length <= lines.length);
        for (int i = 0; i < bootstrapClasses.length; i++) {
            assertEquals(i + " -> " + bootstrapClasses[i] + " (map: " + i + ")", lines[i]);
        }
    }

}
