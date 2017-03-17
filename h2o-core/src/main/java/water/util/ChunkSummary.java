package water.util;

import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.CategoricalWrappedVec;
import water.fvec.Vec;

/**
 * Simple summary of how many chunks of each type are in a Frame
 */
public class ChunkSummary extends MRTask<ChunkSummary> {

  ChunkSummary() {  super((byte)(Thread.currentThread() instanceof H2O.FJWThr ? currThrPriority()+1 : H2O.MIN_HI_PRIORITY - 2)); }

  public static final String[][] chunkTypes = new String[][]{

      {"C0L","Constant long"},
      {"C0D","Constant double"},
      {"CBS","Binary"},
      {"CXI","Sparse Integers"},
      {"CXS","Sparse Fractions"},
      {"CXF","Sparse Reals"},
      {"C1","1-Byte Integers"},
      {"C1N","1-Byte Integers (w/o NAs)"},
      {"C1S","1-Byte Fractions"},
      {"C2","2-Byte Integers"},
      {"C2S","2-Byte Fractions"},
      {"C4","4-Byte Integers"},
      {"C4S","4-Byte Fractions"},
      {"C4F","4-byte Reals"},
      {"C8","8-byte Integers"},
      {"C16","UUIDs"},
      {"CStr","Strings"},
      {"CUD","Unique Reals"},
      {"C8D","64-bit Reals"},
  };


  // OUTPUT
  private long[] chunk_counts;
  private long total_chunk_count;
  private long[] chunk_byte_sizes;

  private long total_chunk_byte_size;
  private long[] byte_size_per_node; //averaged over all chunks
  private double byte_size_per_node_mean;
  private double byte_size_per_node_min;
  private double byte_size_per_node_max;
  private double byte_size_per_node_stddev;

  private long total_row_count;
  private long[] row_count_per_node;
  private double row_count_per_node_mean;
  private double row_count_per_node_min;
  private double row_count_per_node_max;
  private double row_count_per_node_stddev;

  private long total_chunk_count_per_col;
  private long[] chunk_count_per_col_per_node;
  private double chunk_count_per_col_per_node_mean;
  private double chunk_count_per_col_per_node_min;
  private double chunk_count_per_col_per_node_max;
  private double chunk_count_per_col_per_node_stddev;

  @Override
  public void map(Chunk[] cs) {
    chunk_counts = new long[chunkTypes.length];
    chunk_byte_sizes = new long[chunkTypes.length];
    byte_size_per_node = new long[H2O.CLOUD.size()];
    row_count_per_node = new long[H2O.CLOUD.size()];
    chunk_count_per_col_per_node = new long[H2O.CLOUD.size()];
    for( Chunk c : cs ) {       // Can be a big loop, for high column counts
      // Pull out the class name; trim a trailing "Chunk"
      String cname = c.getClass().getSimpleName();
      int nlen = cname.length();
      assert nlen > 5 && cname.charAt(nlen-5)=='C' && cname.charAt(nlen-1)=='k';
      String sname = cname.substring(0,nlen-5);
      if (sname.equals("CategoricalWrapped")) {
        Chunk ec = ((CategoricalWrappedVec.CategoricalWrappedChunk)c)._c;
        cname = ec.getClass().getSimpleName();
        nlen = cname.length();
        assert nlen > 5 && cname.charAt(nlen-5)=='C' && cname.charAt(nlen-1)=='k';
        sname = cname.substring(0,nlen-5);
      }
      // Table lookup, roughly sorted by frequency
      int j;
      for( j = 0; j < chunkTypes.length; ++j )
        if( sname.equals(chunkTypes[j][0]) )
          break;
      if( j==chunkTypes.length ) throw H2O.fail("Unknown Chunk Type: " + sname);
      chunk_counts[j]++;
      chunk_byte_sizes[j] += c.byteSize();
      byte_size_per_node[H2O.SELF.index()] += c.byteSize();
    }
    row_count_per_node[H2O.SELF.index()] += cs[0].len();
    total_row_count +=  cs[0].len();
    chunk_count_per_col_per_node[H2O.SELF.index()]++;
    total_chunk_count_per_col++;
  }

  @Override
  public void reduce(ChunkSummary mrt) {
    ArrayUtils.add(chunk_counts,mrt.chunk_counts);
    ArrayUtils.add(chunk_byte_sizes,mrt.chunk_byte_sizes);
    ArrayUtils.add(byte_size_per_node,mrt.byte_size_per_node);
    ArrayUtils.add(row_count_per_node,mrt.row_count_per_node);
    ArrayUtils.add(chunk_count_per_col_per_node,mrt.chunk_count_per_col_per_node);
    total_row_count += mrt.total_row_count;
    total_chunk_count_per_col += mrt.total_chunk_count_per_col;
  }

  @Override
  protected void postGlobal() {
    if (chunk_counts == null || chunk_byte_sizes == null || byte_size_per_node == null) return;
    assert(total_row_count == _fr.numRows()): "total_row_count["+total_row_count+"] != _fr.numRows()["+_fr.numRows()+"]. ";

    // compute counts and sizes
    total_chunk_byte_size = 0;
    total_chunk_count = 0;
    for (int j = 0; j < chunkTypes.length; ++j) {
      total_chunk_byte_size += chunk_byte_sizes[j];
      total_chunk_count += chunk_counts[j];
    }

    long check = 0;
    for (Vec v : _fr.vecs())
      check += v.nChunks();
    assert(total_chunk_count == check);

    // This doesn't always hold, FileVecs have File-based byte size, while Vecs have Chunk-based byte size.
//    assert(total_chunk_byte_size == _fr.byteSize());

    double[] res=MathUtils.min_max_mean_stddev(byte_size_per_node);
    byte_size_per_node_min = res[0];
    byte_size_per_node_max = res[1];
    byte_size_per_node_mean = res[2];
    byte_size_per_node_stddev = res[3];

    res=MathUtils.min_max_mean_stddev(row_count_per_node);
    row_count_per_node_min = res[0];
    row_count_per_node_max = res[1];
    row_count_per_node_mean = res[2];
    row_count_per_node_stddev = res[3];

    res=MathUtils.min_max_mean_stddev(chunk_count_per_col_per_node);
    chunk_count_per_col_per_node_min = res[0];
    chunk_count_per_col_per_node_max = res[1];
    chunk_count_per_col_per_node_mean = res[2];
    chunk_count_per_col_per_node_stddev = res[3];
  }

  String display(long val) { return String.format("%10s", val == 0 ? "  0  B" : PrettyPrint.bytes(val)); }

  public TwoDimTable toTwoDimTableChunkTypes() {
    final String tableHeader = "Chunk compression summary";
    int rows = 0;
    for (int j = 0; j < chunkTypes.length; ++j) if (chunk_counts != null && chunk_counts[j] > 0) rows++;
    final String[] rowHeaders = new String[rows];
    final String[] colHeaders = new String[]{"Chunk Type", "Chunk Name", "Count", "Count Percentage", "Size", "Size Percentage"};
    final String[] colTypes = new String[]{"string", "string", "int", "float", "string", "float"};
    final String[] colFormats = new String[]{"%8s", "%s", "%10d", "%10.3f %%", "%10s", "%10.3f %%"};
    final String colHeaderForRowHeaders = null;
    TwoDimTable table = new TwoDimTable(tableHeader, null, rowHeaders, colHeaders, colTypes, colFormats, colHeaderForRowHeaders);

    int row = 0;
    for (int j = 0; j < chunkTypes.length; ++j) {
      if (chunk_counts != null && chunk_counts[j] > 0) {
        table.set(row, 0, chunkTypes[j][0]);
        table.set(row, 1, chunkTypes[j][1]);
        table.set(row, 2, chunk_counts[j]);
        table.set(row, 3, (float) chunk_counts[j] / total_chunk_count * 100.f);
        table.set(row, 4, display(chunk_byte_sizes[j]));
        table.set(row, 5, (float) chunk_byte_sizes[j] / total_chunk_byte_size * 100.f);
        row++;
      }
    }
    return table;
  }

  public TwoDimTable toTwoDimTableDistribution() {
    final String tableHeader = "Frame distribution summary";
    int rows = H2O.CLOUD.size() + 5;
    final String[] rowHeaders = new String[rows];
    int row;
    for (row=0; row<rows-5; ++row) {
      rowHeaders[row] = H2O.CLOUD._memary[row].getIpPortString();
    }
    rowHeaders[row++] = "mean";
    rowHeaders[row++] = "min";
    rowHeaders[row++] = "max";
    rowHeaders[row++] = "stddev";
    rowHeaders[row  ] = "total";
    final String[] colHeaders = new String[]{"Size", "Number of Rows", "Number of Chunks per Column", "Number of Chunks"};
    final String[] colTypes = new String[]{"string", "float", "float", "float"};
    final String[] colFormats = new String[]{"%s", "%f", "%f", "%f"};
    final String colHeaderForRowHeaders = "";
    TwoDimTable table = new TwoDimTable(tableHeader, null, rowHeaders, colHeaders, colTypes, colFormats, colHeaderForRowHeaders);

    for (row = 0; row < rows-5; ++row) {
      if (byte_size_per_node != null) {
        table.set(row, 0, display(byte_size_per_node[row]));
        table.set(row, 1, row_count_per_node[row]);
        table.set(row, 2, chunk_count_per_col_per_node[row]);
        table.set(row, 3, _fr.numCols() * chunk_count_per_col_per_node[row]);
      }
    }
    table.set(row, 0, display((long)byte_size_per_node_mean));
    table.set(row, 1, row_count_per_node_mean);
    table.set(row, 2, chunk_count_per_col_per_node_mean);
    table.set(row++, 3, _fr.numCols()*chunk_count_per_col_per_node_mean);

    table.set(row, 0, display((long)byte_size_per_node_min));
    table.set(row, 1, row_count_per_node_min);
    table.set(row, 2, chunk_count_per_col_per_node_min);
    table.set(row++, 3, _fr.numCols()*chunk_count_per_col_per_node_min);

    table.set(row, 0, display((long)byte_size_per_node_max));
    table.set(row, 1, row_count_per_node_max);
    table.set(row, 2, chunk_count_per_col_per_node_max);
    table.set(row++, 3, _fr.numCols()*chunk_count_per_col_per_node_max);

    table.set(row, 0, display((long)byte_size_per_node_stddev));
    table.set(row, 1, row_count_per_node_stddev);
    table.set(row, 2, chunk_count_per_col_per_node_stddev);
    table.set(row++, 3, _fr.numCols()*chunk_count_per_col_per_node_stddev);

    table.set(row, 0, display(total_chunk_byte_size));
    table.set(row, 1, total_row_count);
    table.set(row, 2, total_chunk_count_per_col);
    table.set(row, 3, _fr.numCols()*total_chunk_count_per_col);

    return table;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(toTwoDimTableChunkTypes().toString());
    sb.append(toTwoDimTableDistribution().toString());
    if (H2O.CLOUD.size() > 1 && byte_size_per_node_stddev > 0.2 * byte_size_per_node_mean) {
      sb.append("** Note: Dataset is not well distributed, consider rebalancing **\n");
    }
    return sb.toString();
  }
}
