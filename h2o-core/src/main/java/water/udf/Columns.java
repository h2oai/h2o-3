package water.udf;

import water.fvec.Chunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.PrettyPrint;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * An adapter to Vec, allows type-safe access to data
 */
public class Columns {

  public abstract static class DoubleChunks implements ChunkFactory<Double> {
    @Override public byte typeCode() { return Vec.T_NUM; }
  }

  public abstract static class StringChunks implements ChunkFactory<String> {
    @Override public byte typeCode() { return Vec.T_STR; }
  }

  public abstract static class EnumChunks implements ChunkFactory<String> {
    @Override public byte typeCode() { return Vec.T_CAT; }
  }

  public abstract static class UuidChunks implements ChunkFactory<String> {
    @Override public byte typeCode() { return Vec.T_UUID; }
  }

  public abstract static class DateChunks implements ChunkFactory<String> {
    @Override public byte typeCode() { return Vec.T_UUID; }
  }
}