package hex.tfidf;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.parser.BufferedString;
import water.util.IcedDouble;
import water.util.IcedLong;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class InverseDocumentFrequencyTest extends TestUtil {
    
    private static final double IDF_DELTA = 0.0001;

    @BeforeClass()
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testDF() {
        Map<BufferedString, IcedLong>[] termFrequencies = getSimpleTermFrequencies();

        Map<String, IcedLong> expectedDocumentFrequencies = new HashMap() {{
                put("A", new IcedLong(2));
                put("B", new IcedLong(2));
                put("C", new IcedLong(2));
                put("Z", new IcedLong(1));
        }};

        InverseDocumentFrequency idfTask = new InverseDocumentFrequency(termFrequencies.length);
        idfTask.computeIDF(termFrequencies);
        Map<BufferedString, IcedLong> df = idfTask._documentFrequencies;

        for (Map.Entry<String, IcedLong> expEntry : expectedDocumentFrequencies.entrySet())
            assertEquals(expEntry.getValue(), df.get(new BufferedString(expEntry.getKey())));
    }

    @Test
    public void testIDF() {
        Map<BufferedString, IcedLong>[] termFrequencies = getSimpleTermFrequencies();

        Map<String, Double> expectedInverseDocumentFrequencies = new HashMap() {{
            put("A", 0.28768);
            put("B", 0.28768);
            put("C", 0.28768);
            put("Z", 0.69314);
        }};

        InverseDocumentFrequency idfTask = new InverseDocumentFrequency(termFrequencies.length);
        idfTask.computeIDF(termFrequencies);
        Map<BufferedString, IcedDouble> idf = idfTask._inverseDocumentFrequencies;

        for (Map.Entry<String, Double> expEntry : expectedInverseDocumentFrequencies.entrySet())
            assertEquals(expEntry.getValue(), idf.get(new BufferedString(expEntry.getKey()))._val, IDF_DELTA);
    }

    private Map<BufferedString, IcedLong>[] getSimpleTermFrequencies() {
        Map<BufferedString, IcedLong>[] termFrequencies = new HashMap[] {
                new HashMap<BufferedString, IcedLong>() {{
                    put(new BufferedString("A"), new IcedLong(1));
                    put(new BufferedString("B"), new IcedLong(1));
                    put(new BufferedString("C"), new IcedLong(1));
                }},
                new HashMap<BufferedString, IcedLong>() {{
                    put(new BufferedString("A"), new IcedLong(3));
                    put(new BufferedString("Z"), new IcedLong(1));
                }},
                new HashMap<BufferedString, IcedLong>() {{
                    put(new BufferedString("B"), new IcedLong(1));
                    put(new BufferedString("C"), new IcedLong(3));
                }}
        };
        
        return termFrequencies;
    }
}
