package water.util;
import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Vec;

/**
 * Simple summary of how many chunks of each type are in a Frame
 */
public class ChunkSummary extends MRTask<ChunkSummary> {

  // static list of chunks for which statistics are to be gathered
  final transient static String[] chunkTypes = new String[]{
          "C0L",
          "C0D",
          "CBS",
          "C1",
          "C1N",
          "C1S",
          "C2",
          "C2S",
          "C4",
          "C4S",
          "C4F",
          "C8",
          "C16",
          "CXI",
          "CXD",
          "CX0",
          "C8D", //leave this as last -> no compression
  };

  // OUTPUT
  private long[] chunk_counts;
  private long total_chunk_count;
  private long[] chunk_byte_sizes;
  private long total_chunk_byte_size;
  private long[] byte_size_per_node; //averaged over all chunks
  private float byte_size_per_node_mean;
  private float byte_size_per_node_min;
  private float byte_size_per_node_max;
  private float byte_size_per_node_stddev;

  @Override
  public void map(Chunk[] cs) {
    chunk_counts = new long[chunkTypes.length];
    chunk_byte_sizes = new long[chunkTypes.length];
    byte_size_per_node = new long[H2O.CLOUD.size()];
    for (Chunk c : cs) {
      boolean found = false;
      for (int j = 0; j < chunkTypes.length; ++j) {
        if (c.getClass().getSimpleName().equals(chunkTypes[j] + "Chunk")) {
          found = true;
          chunk_counts[j]++;
          chunk_byte_sizes[j] += c.byteSize();
          byte_size_per_node[H2O.SELF.index()] += c.byteSize();
        }
      }
      if (!found) {
        throw H2O.unimpl();
      }
    }
  }

  @Override
  public void reduce(ChunkSummary mrt) {
    if (mrt.chunk_counts == chunk_counts) return;

    for (int j = 0; j < chunkTypes.length; ++j) {
      chunk_counts[j] += mrt.chunk_counts[j];
      chunk_byte_sizes[j] += mrt.chunk_byte_sizes[j];
    }
    for (int i = 0; i<H2O.CLOUD.size(); ++i) {
      byte_size_per_node[i] += mrt.byte_size_per_node[i];
    }
  }

  @Override
  protected void postGlobal() {
    if (chunk_counts == null || chunk_byte_sizes == null || byte_size_per_node == null) return;

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
    assert(total_chunk_byte_size == _fr.byteSize());

    // compute min, max, mean
    byte_size_per_node_min = Float.MAX_VALUE;
    byte_size_per_node_max = Float.MIN_VALUE;
    byte_size_per_node_mean = 0;
    for (long aByte_size_per_node : byte_size_per_node) {
      byte_size_per_node_min = Math.min(aByte_size_per_node, byte_size_per_node_min);
      byte_size_per_node_max = Math.max(aByte_size_per_node, byte_size_per_node_max);
      byte_size_per_node_mean += aByte_size_per_node;
    }
    byte_size_per_node_mean /= byte_size_per_node.length;

    // compute standard deviation (doesn't have to be single pass...)
    byte_size_per_node_stddev = 0;
    for (long aByte_size_per_node : byte_size_per_node) {
      byte_size_per_node_stddev += Math.pow(aByte_size_per_node - byte_size_per_node_mean, 2);
    }
    byte_size_per_node_stddev /= byte_size_per_node.length;
    byte_size_per_node_stddev = (float)Math.sqrt(byte_size_per_node_stddev);
  }

  String display(long val) { return String.format("%10s", val == 0 ? "  0  B" : PrettyPrint.bytes(val)); }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Internal FluidVec compression/distribution summary:\n");
    sb.append("Chunk type    count     fraction       size     rel. size\n");
    for (int j = 0; j < chunkTypes.length; ++j) {
      if (chunk_counts[j] > 0)
        sb.append(String.format("%8s %10d %10.3f %% %10s %10.3f %%\n",
                chunkTypes[j],
                chunk_counts[j],
                (float) chunk_counts[j] / total_chunk_count * 100.,
                display(chunk_byte_sizes[j]),
                (float) chunk_byte_sizes[j] / total_chunk_byte_size * 100.));
    }
    // if more than 50% is double data, inform the user to consider compressing to single precision
//    if ((float)chunk_byte_sizes[chunk_byte_sizes.length-1] / total_chunk_byte_size > 0.5 && !H2O.SINGLE_PRECISION) {
//      sb.append("** Warning: Significant amount of double precision data (C8DChunk),\n" +
//              "   consider launching with -single_precision to reduce memory consumption **\n");
//    }
    // if standard deviation is more than 20% of mean, then show detailed per-node distribution
    if (byte_size_per_node_stddev > 0.2 * byte_size_per_node_mean) {
      sb.append("** Note: Dataset is not well distributed, consider rebalancing **\n");
      for (int i = 0; i < byte_size_per_node.length; ++i) {
        sb.append("     size on node " + i + " : " + display(byte_size_per_node[i]) + "\n");
      }
    }
    // display chunk distribution
    if (byte_size_per_node.length > 1) {
      sb.append(" mean size per node : " + display((long) byte_size_per_node_mean) + "\n");
      sb.append("  min size per node : " + display((long) byte_size_per_node_min) + "\n");
      sb.append("  max size per node : " + display((long) byte_size_per_node_max) + "\n");
      sb.append("stddev of node size : " + display((long) byte_size_per_node_stddev) + "\n");
    }
    sb.append(" Total memory usage : " + display(total_chunk_byte_size) + "\n");
    return sb.toString();
  }
}
