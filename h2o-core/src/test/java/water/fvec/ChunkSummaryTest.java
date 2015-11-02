package water.fvec;

import hex.CreateFrame;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.api.FrameV3;
import water.api.Schema;
import water.api.TwoDimTableBase;
import water.util.*;

public class ChunkSummaryTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void run() {
    CreateFrame cf = new CreateFrame();
    cf.seed = 1234;
    Frame f = cf.execImpl().get();
    ChunkSummary cs = FrameUtils.chunkSummary(f);
    TwoDimTable chunk_summary_table = cs.toTwoDimTableChunkTypes();
    Log.info(chunk_summary_table);
    String json = new TwoDimTableBase().fillFromImpl(chunk_summary_table).toJsonString();
    Assert.assertEquals(json, "{\"__meta\":{\"schema_version\":-1,\"schema_name\":\"TwoDimTableBase\",\"schema_type\":\"TwoDimTable\"},\"name\":\"Chunk compression summary\",\"description\":\"\",\"columns\":[{\"__meta\":{\"schema_version\":-1,\"schema_name\":\"ColumnSpecsBase\",\"schema_type\":\"Iced\"},\"name\":\"chunk_type\",\"type\":\"string\",\"format\":\"%8s\",\"description\":\"Chunk Type\"},{\"__meta\":{\"schema_version\":-1,\"schema_name\":\"ColumnSpecsBase\",\"schema_type\":\"Iced\"},\"name\":\"chunk_name\",\"type\":\"string\",\"format\":\"%s\",\"description\":\"Chunk Name\"},{\"__meta\":{\"schema_version\":-1,\"schema_name\":\"ColumnSpecsBase\",\"schema_type\":\"Iced\"},\"name\":\"count\",\"type\":\"int\",\"format\":\"%10d\",\"description\":\"Count\"},{\"__meta\":{\"schema_version\":-1,\"schema_name\":\"ColumnSpecsBase\",\"schema_type\":\"Iced\"},\"name\":\"count_percentage\",\"type\":\"float\",\"format\":\"%10.3f %%\",\"description\":\"Count Percentage\"},{\"__meta\":{\"schema_version\":-1,\"schema_name\":\"ColumnSpecsBase\",\"schema_type\":\"Iced\"},\"name\":\"size\",\"type\":\"string\",\"format\":\"%10s\",\"description\":\"Size\"},{\"__meta\":{\"schema_version\":-1,\"schema_name\":\"ColumnSpecsBase\",\"schema_type\":\"Iced\"},\"name\":\"size_percentage\",\"type\":\"float\",\"format\":\"%10.3f %%\",\"description\":\"Size Percentage\"}],\"rowcount\":4,\"data\":[[\"CXI\",\"C1\",\"C1S\",\"C8D\"],[\"Sparse Integers\",\"1-Byte Integers\",\"1-Byte Fractions\",\"64-bit Reals\"],[1,2,2,5],[10.0,20.0,20.0,50.0],[\"    989  B\",\"   19.7 KB\",\"   19.7 KB\",\"  391.0 KB\"],[0.2239416,4.559442,4.5666876,90.649925]]}");

    TwoDimTable distribution_summary_table = cs.toTwoDimTableDistribution();
    Log.info(distribution_summary_table);
    json = new TwoDimTableBase().fillFromImpl(distribution_summary_table).toJsonString();
    if (H2O.CLOUD.size() == 1) {
      Assert.assertEquals(json, "{\"__meta\":{\"schema_version\":-1,\"schema_name\":\"TwoDimTableBase\",\"schema_type\":\"TwoDimTable\"},\"name\":\"Frame distribution summary\",\"description\":\"\",\"columns\":[{\"__meta\":{\"schema_version\":-1,\"schema_name\":\"ColumnSpecsBase\",\"schema_type\":\"Iced\"},\"name\":\"\",\"type\":\"string\",\"format\":\"%s\",\"description\":\"\"},{\"__meta\":{\"schema_version\":-1,\"schema_name\":\"ColumnSpecsBase\",\"schema_type\":\"Iced\"},\"name\":\"size\",\"type\":\"string\",\"format\":\"%s\",\"description\":\"Size\"},{\"__meta\":{\"schema_version\":-1,\"schema_name\":\"ColumnSpecsBase\",\"schema_type\":\"Iced\"},\"name\":\"number_of_rows\",\"type\":\"float\",\"format\":\"%f\",\"description\":\"Number of Rows\"},{\"__meta\":{\"schema_version\":-1,\"schema_name\":\"ColumnSpecsBase\",\"schema_type\":\"Iced\"},\"name\":\"number_of_chunks_per_column\",\"type\":\"float\",\"format\":\"%f\",\"description\":\"Number of Chunks per Column\"},{\"__meta\":{\"schema_version\":-1,\"schema_name\":\"ColumnSpecsBase\",\"schema_type\":\"Iced\"},\"name\":\"number_of_chunks\",\"type\":\"float\",\"format\":\"%f\",\"description\":\"Number of Chunks\"}],\"rowcount\":6,\"data\":[[\"" + H2O.SELF.getIpPortString() + "\",\"mean\",\"min\",\"max\",\"stddev\",\"total\"],[\"  431.3 KB\",\"  431.3 KB\",\"  431.3 KB\",\"  431.3 KB\",\"      0  B\",\"  431.3 KB\"],[10000.0,10000.0,10000.0,10000.0,0.0,10000.0],[1.0,1.0,1.0,1.0,0.0,1.0],[10.0,10.0,10.0,10.0,0.0,10.0]]}");
    }

    f.remove();
  }

}

