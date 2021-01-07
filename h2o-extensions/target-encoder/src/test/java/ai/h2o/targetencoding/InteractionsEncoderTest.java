package ai.h2o.targetencoding;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.stream.IntStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class InteractionsEncoderTest {

    @Test
    public void test_2_interactions() {
        String[][] domains = new String[][] {
                new String[] {"a", "b"},
                new String[] {"A", "B", "C"},
        };
        InteractionsEncoder enc = new InteractionsEncoder(domains, true);
        
        String[] input = new String[]{"b", "C"};
        long encVal = enc.encodeStr(input);
        assertEquals(1+(3*2), encVal);  //b -> 1, C -> (len(a, b)+1) * 2
        assertArrayEquals(input, enc.decodeStr(encVal));
        
        String[] withNA = new String[] {null, "B"};
        long withNAEnc = enc.encodeStr(withNA);
        assertEquals(2+(3*1), withNAEnc);
        assertArrayEquals(withNA, enc.decodeStr(withNAEnc));

        String[] allNAs = new String[] {null, null};
        long allNAsEnc = enc.encodeStr(allNAs);
        assertEquals(2+(3*3), allNAsEnc);
        assertArrayEquals(allNAs, enc.decodeStr(allNAsEnc));

        String[] withUnseen = new String[] {"a", "F"};
        long withUnseenEnc = enc.encodeStr(withUnseen);
        assertEquals(0+(3*3), withUnseenEnc);
        assertArrayEquals(new String[] {"a", null}, enc.decodeStr(withUnseenEnc));
    }
    
    @Test
    public void test_5_interactions() {
        String[][] domains = new String[][] {
           new String[] {"a", "b"},
           new String[] {"A", "B", "C"},
           new String[] {"aa", "bb"}, 
           new String[] {"AA", "BB", "CC", "DD"}, 
           new String[] {"aaa", "bbb", "ccc", "ddd", "eee"},
        };
        InteractionsEncoder enc = new InteractionsEncoder(domains, true);
        
        String[] input = new String[]{"b", "C", "aa", "BB", "eee"};
        long encVal = enc.encodeStr(input);
        assertArrayEquals(input, enc.decodeStr(encVal));

        String[] withNAsAndUnseen = new String[]{"b", null, "aa", "BBD", "eee"};
        assertArrayEquals(new String[] {"b", null, "aa", null, "eee"}, enc.decodeStr(enc.encodeStr(withNAsAndUnseen)));
    }
    
}
