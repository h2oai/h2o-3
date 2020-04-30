package hex.tree.xgboost.predict;

import hex.tree.xgboost.util.FeatureScore;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class XGBoostNativeVariableImportanceTest {

    @Test
    public void parseFeatureScores() throws IOException, ParseException {
        String[] modelDump = readLines(getClass().getResource("xgbdump.txt"));
        String[] expectedVarImps = readLines(getClass().getResource("xgbvarimps.txt"));

        Map<String, FeatureScore> scores = XGBoostNativeVariableImportance.parseFeatureScores(modelDump);
        double totalGain = 0;
        double totalCover = 0;
        double totalFrequency = 0;
        for (FeatureScore score : scores.values()) {
            totalGain += score._gain;
            totalCover += score._cover;
            totalFrequency += score._frequency;
        }

        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        for (String varImp : expectedVarImps) {
            String[] vals = varImp.split(" ");
            FeatureScore score = scores.get(vals[0]);
            assertNotNull("Score " + vals[0] + " should ve calculated", score);
            float expectedGain = nf.parse(vals[1]).floatValue();
            assertEquals("Gain of " + vals[0], expectedGain, score._gain / totalGain, 1e-6);
            float expectedCover = nf.parse(vals[2]).floatValue();
            assertEquals("Cover of " + vals[0], expectedCover, score._cover / totalCover, 1e-6);
            float expectedFrequency = nf.parse(vals[3]).floatValue();
            assertEquals("Frequency of " + vals[0], expectedFrequency, score._frequency / totalFrequency, 1e-6);
        }
    }

    private static String[] readLines(URL url) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines.toArray(new String[0]);
    }

}
