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
package org.apache.parquet.hadoop;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;

import org.apache.parquet.format.converter.ParquetMetadataConverter;
import static org.apache.parquet.format.converter.ParquetMetadataConverter.MetadataFilter;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.schema.MessageType;
import water.fvec.Vec;
import water.parser.ParseWriter;
import water.parser.parquet.ChunkReadSupport;
import water.parser.parquet.VecDataInputStream;
import water.parser.parquet.VecFileSystem;
import water.util.Log;

import static org.apache.parquet.bytes.BytesUtils.readIntLittleEndian;
import static org.apache.parquet.hadoop.ParquetFileWriter.MAGIC;

/**
 * Implementation of Parquet Reader working on H2O's Vecs.
 *
 * Note: This class was derived from Parquet's ParquetReader implementation. We cannot directly
 * use the original implementation because it uses Hadoop FileSystem to access source data (and also Parquet summary files),
 * it uses its own parallel implementation for reading metadata information which doesn't fit into H2O's architecture.
 * We need to keep this class in package "org.apache.parquet.hadoop" to get access to Parquet's InternalParquetRecordReader.
 */
public class VecParquetReader implements Closeable {

  private static ParquetMetadataConverter converter = new ParquetMetadataConverter();

  private final Vec vec;
  private final ParquetMetadata metadata;
  private final ParseWriter writer;
  private final byte[] chunkSchema;

  private InternalParquetRecordReader<Integer> reader;

  public VecParquetReader(Vec vec, ParquetMetadata metadata, ParseWriter writer, byte[] chunkSchema) {
    this.vec = vec;
    this.metadata = metadata;
    this.writer = writer;
    this.chunkSchema = chunkSchema;
  }

  /**
   * @return the index of added Chunk record or null if finished
   * @throws IOException
   */
  public Integer read() throws IOException {
    try {
      if (reader == null) {
        initReader();
      }
      assert reader != null;
      if (reader.nextKeyValue()) {
        return reader.getCurrentValue();
      } else {
        return null;
      }
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  private void initReader() throws IOException {
    assert reader == null;
    List<BlockMetaData> blocks = metadata.getBlocks();
    MessageType fileSchema = metadata.getFileMetaData().getSchema();
    reader = new InternalParquetRecordReader<>(new ChunkReadSupport(writer, chunkSchema));
    Configuration conf = VecFileSystem.makeConfiguration(vec);
    reader.initialize(fileSchema, metadata.getFileMetaData().getKeyValueMetaData(), VecFileSystem.VEC_PATH, blocks, conf);
  }

  @Override
  public void close() throws IOException {
    if (reader != null) {
      reader.close();
    }
  }

  public static ParquetMetadata readFooter(Vec vec, MetadataFilter filter) {
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
      return converter.readParquetMetadata(f, filter);
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

}
