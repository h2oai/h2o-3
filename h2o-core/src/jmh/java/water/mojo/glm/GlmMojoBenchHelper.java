package water.mojo.glm;

import au.com.bytecode.opencsv.CSVReader;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;

import java.io.*;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GlmMojoBenchHelper {

  static void readData(File f, int cols, String firstColName, double[][] out, MojoModel mojo) throws IOException {
    int[] mapping = new int[cols];
    for (int i = 0; i < cols; i++) mapping[i] = i;
    readData(f, mapping, firstColName, out, mojo);
  }

  static void readData(File f, int[] mapping, String firstColName, double[][] out, MojoModel mojo) throws IOException {
    InputStream is = new FileInputStream(f);
    try {
      InputStream source;
      if (f.getName().endsWith(".zip")) {
        ZipInputStream zis = new ZipInputStream(is);
        ZipEntry entry = zis.getNextEntry();
        if (! entry.getName().endsWith(".csv")) throw new IllegalStateException("CSV file expected, name " + entry.getName());
        source = zis;
      } else {
        source = new GZIPInputStream(is);
      }
      CSVReader r = new CSVReader(new InputStreamReader(source));
      if (firstColName != null) {
        String[] header = r.readNext();
        if (header == null) throw new IllegalStateException("File empty");
        if (! firstColName.equals(header[0])) throw new IllegalStateException("Header expected");
      }
      int rowIdx = 0;
      String[] row;
      while ((rowIdx < out.length) && ((row = r.readNext()) != null)) {
        double[] outRow = out[rowIdx++];
        if (row.length < mapping.length)
          throw new IllegalStateException("Row too short: " + Arrays.toString(row));
        for (int i = 0; i < mapping.length; i++) {
          int target = mapping[i];
          if (target < 0)
            continue;
          if ("NA".equals(row[i])) {
            outRow[target] = Double.NaN;
            continue;
          }
          String[] domain = mojo.getDomainValues(target);
          if (domain == null)
            outRow[target] = Double.parseDouble(row[i]);
          else {
            outRow[target] = -1;
            for (int d = 0; d < domain.length; d++)
              if (domain[d].equals(row[i])) {
                outRow[target] = d;
                break;
              }
            if (outRow[target] < 0)
              throw new IllegalStateException("Value " + row[i] + " not found in domain " + Arrays.toString(domain));
          }

        }
      }
    } finally {
      is.close();
    }
  }

  static MojoModel loadMojo(String dir) throws IOException {
    return ModelMojoReader.readFrom(new ClasspathReaderBackend(dir));
  }

  private static class ClasspathReaderBackend implements MojoReaderBackend {

    private final String _dir;

    public ClasspathReaderBackend(String dir) { _dir = dir; }

    @Override
    public BufferedReader getTextFile(String filename) throws IOException {
      InputStream is = GlmMojoBenchHelper.class.getResourceAsStream(_dir + "/" + filename);
      return new BufferedReader(new InputStreamReader(is));
    }

    @Override
    public byte[] getBinaryFile(String filename) throws IOException {
      throw new UnsupportedOperationException("Unexpected call to getBinaryFile()");
    }

    @Override
    public boolean exists(String name) {
      throw new UnsupportedOperationException("Unexpected call to exists()");
    }
  }
}
