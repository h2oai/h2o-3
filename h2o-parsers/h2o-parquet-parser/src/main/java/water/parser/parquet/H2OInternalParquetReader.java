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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.filter2.compat.FilterCompat;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.api.InitContext;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.FileMetaData;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.ParquetDecodingException;
import org.apache.parquet.io.api.RecordMaterializer;
import org.apache.parquet.io.api.RecordMaterializer.RecordMaterializationException;
import org.apache.parquet.schema.MessageType;

import static java.lang.String.format;
import static org.apache.parquet.Preconditions.checkNotNull;

import water.util.Log;

/**
 * WARNING: H2O-Customized copy of Parquet's InternalParquetRecordReader, see SW-542 for explanation
 * Customization include: similification, H2O's logging 
 */
class H2OInternalParquetRecordReader<T> {

  private ColumnIOFactory columnIOFactory = null;
  private final FilterCompat.Filter filter;

  private MessageType requestedSchema;
  private MessageType fileSchema;
  private int columnCount;
  private final ReadSupport<T> readSupport;

  private RecordMaterializer<T> recordConverter;

  private T currentValue;
  private long total;
  private long current = 0;
  private int currentBlock = -1;
  private ParquetFileReader reader;
  private org.apache.parquet.io.RecordReader<T> recordReader;
  private boolean strictTypeChecking;

  private long totalTimeSpentReadingBytes;
  private long totalTimeSpentProcessingRecords;
  private long startedAssemblingCurrentBlockAt;

  private long totalCountLoadedSoFar = 0;

  private Path file;
  private long unmaterializableRecordCounter = 0;

  /**
   * @param readSupport Object which helps reads files of the given type, e.g. Thrift, Avro.
   * @param filter for filtering individual records
   */
  public H2OInternalParquetRecordReader(ReadSupport<T> readSupport, FilterCompat.Filter filter) {
    this.readSupport = readSupport;
    this.filter = checkNotNull(filter, "filter");
  }

  /**
   * @param readSupport Object which helps reads files of the given type, e.g. Thrift, Avro.
   */
  public H2OInternalParquetRecordReader(ReadSupport<T> readSupport) {
    this(readSupport, FilterCompat.NOOP);
  }

  private void checkRead() throws IOException {
    if (current == totalCountLoadedSoFar) {
      if (current != 0) {
        totalTimeSpentProcessingRecords += (System.currentTimeMillis() - startedAssemblingCurrentBlockAt);
        if (Log.isLoggingFor(Log.Level.INFO)) {
          Log.info("Assembled and processed " + totalCountLoadedSoFar + " records from " + columnCount + " columns in " + totalTimeSpentProcessingRecords + " ms: "+((float)totalCountLoadedSoFar / totalTimeSpentProcessingRecords) + " rec/ms, " + ((float)totalCountLoadedSoFar * columnCount / totalTimeSpentProcessingRecords) + " cell/ms");
          final long totalTime = totalTimeSpentProcessingRecords + totalTimeSpentReadingBytes;
          if (totalTime != 0) {
            final long percentReading = 100 * totalTimeSpentReadingBytes / totalTime;
            final long percentProcessing = 100 * totalTimeSpentProcessingRecords / totalTime;
            Log.info("time spent so far " + percentReading + "% reading ("+totalTimeSpentReadingBytes+" ms) and " + percentProcessing + "% processing ("+totalTimeSpentProcessingRecords+" ms)");
          }
        }
      }

      Log.info("at row " + current + ". reading next block");
      long t0 = System.currentTimeMillis();
      PageReadStore pages = reader.readNextRowGroup();
      if (pages == null) {
        throw new IOException("expecting more rows but reached last block. Read " + current + " out of " + total);
      }
      long timeSpentReading = System.currentTimeMillis() - t0;
      totalTimeSpentReadingBytes += timeSpentReading;
      Log.info("block read in memory in " + timeSpentReading + " ms. row count = " + pages.getRowCount());
      Log.debug("initializing Record assembly with requested schema " + requestedSchema);
      MessageColumnIO columnIO = columnIOFactory.getColumnIO(requestedSchema, fileSchema, strictTypeChecking);
      recordReader = columnIO.getRecordReader(pages, recordConverter, filter);
      startedAssemblingCurrentBlockAt = System.currentTimeMillis();
      totalCountLoadedSoFar += pages.getRowCount();
      ++ currentBlock;
    }
  }

  public void close() throws IOException {
    if (reader != null) {
      reader.close();
    }
  }

  public T getCurrentValue() throws IOException,
          InterruptedException {
    return currentValue;
  }

  public void initialize(MessageType fileSchema,
                         FileMetaData parquetFileMetadata,
                         Path file, List<BlockMetaData> blocks, Configuration configuration)
          throws IOException {
    // initialize a ReadContext for this file
    Map<String, String> fileMetadata = parquetFileMetadata.getKeyValueMetaData();
    ReadSupport.ReadContext readContext = readSupport.init(new InitContext(
            configuration, toSetMultiMap(fileMetadata), fileSchema));
    this.columnIOFactory = new ColumnIOFactory(parquetFileMetadata.getCreatedBy());
    this.requestedSchema = readContext.getRequestedSchema();
    this.fileSchema = fileSchema;
    this.file = file;
    this.columnCount = requestedSchema.getPaths().size();
    this.recordConverter = readSupport.prepareForRead(
            configuration, fileMetadata, fileSchema, readContext);
    this.strictTypeChecking = true;
    List<ColumnDescriptor> columns = requestedSchema.getColumns();
    reader = new ParquetFileReader(configuration, parquetFileMetadata, file, blocks, columns);
    for (BlockMetaData block : blocks) {
      total += block.getRowCount();
    }
    Log.info("RecordReader initialized will read a total of " + total + " records.");
  }

  public boolean nextKeyValue() throws IOException, InterruptedException {
    boolean recordFound = false;

    while (!recordFound) {
      // no more records left
      if (current >= total) { return false; }

      try {
        checkRead();
        current ++;

        try {
          currentValue = recordReader.read();
        } catch (RecordMaterializationException e) {
          // this might throw, but it's fatal if it does.
          unmaterializableRecordCounter++;
          Log.debug("skipping a corrupt record");
          continue;
        }

        if (recordReader.shouldSkipCurrentRecord()) {
          // this record is being filtered via the filter2 package
          Log.debug("skipping record");
          continue;
        }

        if (currentValue == null) {
          // only happens with FilteredRecordReader at end of block
          current = totalCountLoadedSoFar;
          Log.debug("filtered record reader reached end of block");
          continue;
        }

        recordFound = true;

        Log.debug("read value: " + currentValue);
      } catch (RuntimeException e) {
        throw new ParquetDecodingException(format("Can not read value at %d in block %d in file %s", current, currentBlock, file), e);
      }
    }
    return true;
  }

  public long getUnmaterializableRecordCount() {
    return unmaterializableRecordCounter;
  }

  private static <K, V> Map<K, Set<V>> toSetMultiMap(Map<K, V> map) {
    Map<K, Set<V>> setMultiMap = new HashMap<K, Set<V>>();
    for (Map.Entry<K, V> entry : map.entrySet()) {
      Set<V> set = new HashSet<V>();
      set.add(entry.getValue());
      setMultiMap.put(entry.getKey(), Collections.unmodifiableSet(set));
    }
    return Collections.unmodifiableMap(setMultiMap);
  }

}
