package water.fvec;

import hex.genmodel.utils.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static water.TestUtil.ar;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class FrameCSVStreamTest {

    @Test
    public void testWriteToCsvNoEscape() throws IOException {
        Scope.enter();
        try {
            long[] numericalCol = ar(1, 2, 3);
            Frame input = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("Str", "Cat", "Num")
                .withVecTypes(Vec.T_STR, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "\\c\""))
                .withDataForCol(1, ar("X", "X", "\\Y\""))
                .withDataForCol(2, numericalCol)
                .withChunkLayout(numericalCol.length)
                .build();
            final Frame.CSVStreamParams csvStreamParams = new Frame.CSVStreamParams()
                .setHeaders(true)
                .setEscapeQuotes(false);
            final InputStream inputStream = input.toCSV(csvStreamParams);
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copyStream(inputStream, bos);
            String csv = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            String expected = "\"Str\",\"Cat\",\"Num\"\n" +
                "\"a\",\"X\",1\n" +
                "\"b\",\"X\",2\n" +
                "\"\\c\"\",\"\\Y\"\",3\n";
            assertEquals(expected, csv);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testWriteToCsvEscapingDefault() throws IOException {
        Scope.enter();
        try {
            long[] numericalCol = ar(1, 2, 3);
            Frame input = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("Str", "Cat", "Num")
                .withVecTypes(Vec.T_STR, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("w\\v\"z", "b", "c"))
                .withDataForCol(1, ar("X", "X", "W\\V\"Z"))
                .withDataForCol(2, numericalCol)
                .withChunkLayout(numericalCol.length)
                .build();
            final Frame.CSVStreamParams csvStreamParams = new Frame.CSVStreamParams()
                .setHeaders(false)
                .setEscapeQuotes(true);
            final InputStream inputStream = input.toCSV(csvStreamParams);
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copyStream(inputStream, bos);
            String csv = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            String expected = "\"w\\v\"\"z\",\"X\",1\n" +
                "\"b\",\"X\",2\n" +
                "\"c\",\"W\\V\"\"Z\",3\n";
            assertEquals(expected, csv);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testWriteToCsvEscapingCustom() throws IOException {
        Scope.enter();
        try {
            long[] numericalCol = ar(1, 2, 3);
            Frame input = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("Str", "Cat", "Num")
                .withVecTypes(Vec.T_STR, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("w\\v\"z", "b", "c"))
                .withDataForCol(1, ar("X", "X", "W\\V\"Z"))
                .withDataForCol(2, numericalCol)
                .withChunkLayout(numericalCol.length)
                .build();
            final Frame.CSVStreamParams csvStreamParams = new Frame.CSVStreamParams()
                .setHeaders(false)
                .setEscapeQuotes(true)
                .setEscapeChar('\\');
            final InputStream inputStream = input.toCSV(csvStreamParams);
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copyStream(inputStream, bos);
            String csv = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            String expected = "\"w\\\\v\\\"z\",\"X\",1\n" +
                "\"b\",\"X\",2\n" +
                "\"c\",\"W\\\\V\\\"Z\",3\n";
            assertEquals(expected, csv);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testWriteToCsvBig() throws IOException {
        Scope.enter();
        String data = "./smalldata/airlines/AirlinesTrain.csv";
        try {
            Frame fr = parseTestFile(data);
            Scope.track(fr);

            final Frame.CSVStreamParams csvStreamParams = new Frame.CSVStreamParams()
                .setHeaders(true)
                .setEscapeQuotes(false);
            final InputStream inputStream = fr.toCSV(csvStreamParams);
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copyStream(inputStream, bos);
            String csv = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            assertEquals(readCSV(data, true), csv);
        } finally {
            Scope.exit();
        }
    }

    private String readCSV(String path, boolean header) throws IOException {
        File f = FileUtils.locateFile(path);
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            long rowIdx = 0;
            while ((line = r.readLine()) != null) {
                boolean append = true;
                if (rowIdx == 0 && !header) append = false;
                if (append) sb.append(line).append("\n");
                rowIdx++;
            }
            return sb.toString();
        }
    }

}
