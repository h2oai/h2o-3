package hex.genmodel.algos.glrm;

import com.google.common.io.ByteStreams;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import static org.junit.Assert.assertTrue;

public class GlrmMojoModelTest {
  private MojoModel _mojo;
  private double[][] _rows;
  private int[] _permutation;
  private int[] _numLevels;

  @Before
  public void setup() throws IOException {
    _mojo = GlrmMojoReader.readFrom(new GlrmMojoModelTest.ClasspathReaderBackend());
    _rows = new double[][] {
            new double[]{0.0, 1.0, 5.0, 2.0, 741, 912, 5.0, 79.0, 82.0, 447.0, 1.0, 1.0},
            new double[]{0.0, 1.0, 9.0, 6.0, 729.0, 847.0, 5.0, 79.0, 82.0, 447.0, 0.0, -1},
            new double[]{0.0, 1.0, 10.0, 0.0, 749.0, 922.0, 5.0, 79.0, 82.0, 447.0, 1.0, 1.0}};
    _permutation = new int[] {7,8,2,0,6,3,1,10,4,5,9,11};
    _numLevels = new int[] {101, 93, 31, 14, 10, 7, 2, 2, -1, -1, -1, -1};
  }

  @Test
  public void testConvertUnseenEnumsToNA() throws Exception {
    double[][] changedRow = new double[3][];

    for (int rIndex = 0; rIndex < 3; rIndex++) {
      // change one enum column to blow up for each row
      _rows[rIndex][_permutation[rIndex]] = _numLevels[rIndex]+10.0;
      changedRow[rIndex] = ((GlrmMojoModel) _mojo).getRowData(_rows[rIndex]);
      assertTrue(Double.isNaN(changedRow[rIndex][rIndex])); // make sure it is NaN for the blow up enum entry
    }
  }

  private static class ClasspathReaderBackend implements MojoReaderBackend {
    @Override
    public BufferedReader getTextFile(String filename) throws IOException {
      InputStream is = GlrmMojoModelTest.class.getResourceAsStream(filename);
      return new BufferedReader(new InputStreamReader(is));
    }

    @Override
    public byte[] getBinaryFile(String filename) throws IOException {
      InputStream is = GlrmMojoModelTest.class.getResourceAsStream(filename);
      return ByteStreams.toByteArray(is);
    }

    @Override
    public boolean exists(String filename) {
      return true;
    }
  }
}
