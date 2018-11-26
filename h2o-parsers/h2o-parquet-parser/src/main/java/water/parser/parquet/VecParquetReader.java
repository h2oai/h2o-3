/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package water.parser.parquet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.parquet.filter2.compat.FilterCompat;
import org.apache.parquet.filter2.compat.RowGroupFilter;
import org.apache.parquet.format.converter.ParquetMetadataConverter;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import water.fvec.Vec;
import water.parser.ParseWriter;
import water.persist.VecDataInputStream;
import water.persist.VecFileSystem;
import water.util.Log;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;

import static org.apache.parquet.bytes.BytesUtils.readIntLittleEndian;
import static org.apache.parquet.format.converter.ParquetMetadataConverter.MetadataFilter;
import static org.apache.parquet.format.converter.ParquetMetadataConverter.NO_FILTER;
import static org.apache.parquet.hadoop.ParquetFileReader.PARQUET_READ_PARALLELISM;
import static org.apache.parquet.hadoop.ParquetFileWriter.MAGIC;

/**
 * Implementation of Parquet Reader working on H2O's Vecs.
 *
 * Note: This class was derived from Parquet's ParquetReader implementation. We cannot directly
 * use the original implementation because it uses Hadoop FileSystem to access source data (and also Parquet summary files),
 * it uses its own parallel implementation for reading metadata information which doesn't fit into H2O's architecture.
 */
public class VecParquetReader implements Closeable {

  private static ParquetMetadataConverter converter = new ParquetMetadataConverter();

  private final Vec vec;
  private final ParquetMetadata metadata;
  private final WriterDelegate writer;
  private final byte[] chunkSchema; // contains column types of all columns, not just the skipped one

  private ParquetReader<Long> reader;
  private boolean[] _keepColumns;

  public VecParquetReader(Vec vec, ParquetMetadata metadata, ParseWriter writer, byte[] chunkSchema, boolean[] keepcolumns, int parseColumnNumber) {
    this(vec, metadata, new WriterDelegate(writer, parseColumnNumber), chunkSchema, keepcolumns);
  }

  VecParquetReader(Vec vec, ParquetMetadata metadata, WriterDelegate writer, byte[] chunkSchema, boolean[] keepcolumns) {
    this.vec = vec;
    this.metadata = metadata;
    this.writer = writer;
    this.chunkSchema = chunkSchema;
    _keepColumns = keepcolumns;
  }

  /**
   * @return the index of added Chunk record or null if finished
   * @throws IOException
   */
  public Long read() throws IOException {
    if (reader == null) {
      initReader();
    }
    assert reader != null;
    return reader.read();
  }

  private void initReader() throws IOException {
    assert reader == null;
    Configuration conf = VecFileSystem.makeConfiguration(vec);
    conf.setInt(PARQUET_READ_PARALLELISM, 1); // disable parallelism (just one virtual file!)
    ChunkReadSupport crSupport = new ChunkReadSupport(writer, chunkSchema, _keepColumns);
    ParquetReader.Builder<Long> prBuilder = ParquetReader.builder(crSupport, VecFileSystem.VEC_PATH)
            .withConf(conf)
            .withFilter(new FilterCompat.Filter() {
              @Override
              @SuppressWarnings("unchecked")
              public <R> R accept(FilterCompat.Visitor<R> visitor) {
                if (visitor instanceof RowGroupFilter) // inject already filtered metadata on RowGroup level
                  return (R) metadata.getBlocks();
                else // no other filtering otherwise
                  return visitor.visit((FilterCompat.NoOpFilter) FilterCompat.NOOP);
              }
            });
    reader = prBuilder.build();
  }

  @Override
  public void close() throws IOException {
    if (reader != null) {
      reader.close();
    }
  }

  public static byte[] readFooterAsBytes(Vec vec) {
    FSDataInputStream f = null;
    try {
      f = new FSDataInputStream(new VecDataInputStream(vec));
      final int FOOTER_LENGTH_SIZE = 4;
      if (vec.length() < MAGIC.length + FOOTER_LENGTH_SIZE + MAGIC.length) { // MAGIC + data + footer + footerIndex + MAGIC
        throw new RuntimeException("Vec doesn't represent a Parquet data (too short)");
      }
      long footerLengthIndex = vec.length() - FOOTER_LENGTH_SIZE - MAGIC.length;
      f.seek(footerLengthIndex);
      int footerLength = readIntLittleEndian(f);
      byte[] magic = new byte[MAGIC.length];
      f.readFully(magic);
      if (!Arrays.equals(MAGIC, magic)) {
        throw new RuntimeException("Vec is not a Parquet file. expected magic number at tail " +
                Arrays.toString(MAGIC) + " but found " + Arrays.toString(magic));
      }
      long footerIndex = footerLengthIndex - footerLength;
      if (footerIndex < MAGIC.length || footerIndex >= footerLengthIndex) {
        throw new RuntimeException("corrupted file: the footer index is not within the Vec");
      }
      f.seek(footerIndex);
      byte[] metadataBytes = new byte[footerLength];
      f.readFully(metadataBytes);
      return metadataBytes;
    } catch (IOException e) {
      throw new RuntimeException("Failed to read Parquet metadata", e);
    } finally {
      try {
        if (f != null) f.close();
      } catch (Exception e) {
        Log.warn("Failed to close Vec data input stream", e);
      }
    }
  }

  public static ParquetMetadata readFooter(byte[] metadataBytes) {
    return readFooter(metadataBytes, NO_FILTER);
  }

  public static ParquetMetadata readFooter(byte[] metadataBytes, MetadataFilter filter) {
    try {
      ByteArrayInputStream bis = new ByteArrayInputStream(metadataBytes);
      return converter.readParquetMetadata(bis, filter);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read Parquet metadata", e);
    }
  }

}
