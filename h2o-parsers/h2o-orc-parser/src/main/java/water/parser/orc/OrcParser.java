package water.parser.orc;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.vector.*;
import org.apache.hadoop.hive.ql.io.orc.OrcFile;
import org.apache.hadoop.hive.ql.io.orc.Reader;
import org.apache.hadoop.hive.ql.io.orc.RecordReader;
import org.apache.hadoop.hive.ql.io.orc.StripeInformation;
import org.apache.hadoop.hive.serde2.io.HiveDecimalWritable;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import water.Job;
import water.Key;
import water.parser.*;
import water.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static water.parser.orc.OrcUtil.isSupportedSchema;
import static water.parser.orc.OrcUtil.schemaToColumnType;

// Orc support

/**
 * ORC parser for H2O distributed parsing subsystem.
 *
 * Basically, here is the plan:
 * To parse an Orc file, we need to do the following in order to get the following useful
 * information:
 * 1. Get a Reader rdr.
 * 2. From the reader rdr, we can get the following pieces of information:
 *  a. number of columns, column types and column names.  We only support parsing of primitive types;
 *  b. Lists of StripeInformation that describes how many stripes of data that we will need to read;
 *  c. For each stripe, get information like rows per stripe, data size in bytes
 * 3.  The plan is to read the file in parallel in whole numbers of stripes.
 * 4.  Inside each stripe, we will read data out in batches of VectorizedRowBatch (1024 rows or less).
 */
public class OrcParser extends Parser {

  /** Orc Info */
  private final Reader orcFileReader; // can generate all the other fields from this reader

  OrcParser(ParseSetup setup, Key<Job> jobKey) {
    super(setup, jobKey);
    this.orcFileReader = ((OrcParser.OrcParseSetup) setup).orcFileReader;

  }

  /**
   * This method calculates the number of stripes that will be read for each chunk.  Since
   * only single threading is supported in reading each stripe, we will never split one stripe
   * over different chunks.
   *
   * @param cidx: chunk index, calculated as file size/chunk size.  The file size is calculated
   *            with data plus overhead in terms of headers and other info, number of chunks
   *            calculated will be higher than the actual chunks needed.  If the chunk number
   *            is too high, the method will return without writing to
   *            dout.
   * @param din: ParseReader, not used for parsing orc files
   * @param dout: ParseWriter, used to add data to H2O frame.
     * @return: Parsewriter dout.
     */
  @Override
  protected final ParseWriter parseChunk(int cidx, ParseReader din, ParseWriter dout) {

    // figure out which stripes we are trying to read in this thread
    int[] stripeStartEndIndex = new int[2];

    try {
      // only do something if within file size
      if (cidx < ((double) ((OrcParseSetup) this._setup).getTotalFileSize() / this._setup._chunk_size) + 1) {
        // calculate the correct stripe start index and end index
        stripeStartEndIndex = findStripeIndices(cidx, this._setup._chunk_size,
                ((OrcParseSetup) this._setup).getCumstripeSizes());

        List<StripeInformation> stripesInfo = ((OrcParseSetup) this._setup).getStripeInfo();
        Reader fileReader = ((OrcParseSetup) this._setup).getOrcFileReader();

        // prepare parameter to read a orc file.
        boolean[] toInclude = new boolean[this._setup.getColumnNames().length+1];   // must equal to number of column+1
        Arrays.fill(toInclude, true);


        // proceed and read each stripe
        for (int stripeIndex = stripeStartEndIndex[0]; stripeIndex <= stripeStartEndIndex[1]; stripeIndex++) {
          StripeInformation thisStripe = stripesInfo.get(stripeIndex);  // get one stripe

          // write one stripe of data to H2O frame
          write1Stripe(thisStripe, fileReader, toInclude, this._setup.getColumnNames(),
                  ((OrcParseSetup) this._setup).getColumnTypesString(), dout);
        }
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }

    Log.trace(String.format("Orc: ChunkIdx: %d read %d stripes, start at stripe index, end at stripe index", cidx,
            stripeStartEndIndex[1]-stripeStartEndIndex[0]+1, stripeStartEndIndex[0], stripeStartEndIndex[1]));

    return dout;
  }


  /**
   * Find the starting stripe and ending stripe index to read data in for a chunk.
   *
   * @param cidx: integer denoting chunk index;
   * @param chunk_size: integer representing file size in bytes to be read
   * @return: integer array of two elements denoting the starting and ending stripes.
     */
  public int[] findStripeIndices(int cidx, int chunk_size, Long[] cumStripeSizes) {
    int[] tempIndices = new int[2];
    int startingByte = cidx * chunk_size;
    int lastChunkIndex = cumStripeSizes.length-1;

    if (startingByte > cumStripeSizes[lastChunkIndex]) { // last chunk
      startingByte = startingByte - chunk_size;
      for (int index = 0; index < cumStripeSizes.length; index++) {
        if (startingByte <= cumStripeSizes[index]) {
          if (index+1 < cumStripeSizes.length)
            tempIndices[0] = index+1;
          else
            tempIndices[0] = index;
          break;
        }
      }
      tempIndices[1] = lastChunkIndex;
    } else {
      for (int index = 0; index < cumStripeSizes.length; index++) {
        if (startingByte <= cumStripeSizes[index]) {
          tempIndices[0] = index;
          break;
        }
      }

      startingByte = startingByte + chunk_size; // find ending stripe
      for (int index = tempIndices[0]; index < cumStripeSizes.length; index++) {
        if (cumStripeSizes[index] > startingByte) {
          if (index > tempIndices[0])
            tempIndices[1] = index - 1;
          else
            tempIndices[1] = tempIndices[0];
          break;
        }
      }

      // could be at the end of the file or the stripe is the size of the chunk
      if (cumStripeSizes[lastChunkIndex] <= startingByte)
        tempIndices[1] = lastChunkIndex;
    }

    return tempIndices;
  }

  /**
   * This method reads in one stripe of data at a time and write them to a H2O frame.
   *
   * @param oneStripe:  contain information on the stripe
   * @param orcFileReader: Reader pointing to the orc file to be read in
   * @param toInclude: boolean array to denote columns to be read
   * @param columnNames: string array denoting column names;
   * @param columnTypes: string array denoting column types;
     * @param dout
     */
  private static void write1Stripe(StripeInformation oneStripe, Reader orcFileReader, boolean[] toInclude,
                                   String[] columnNames, String[] columnTypes, ParseWriter dout) {
    try {
      RecordReader perStripe = orcFileReader.rows(oneStripe.getOffset(), oneStripe.getDataLength(), toInclude, null,
              columnNames);
      VectorizedRowBatch batch = perStripe.nextBatch(null);  // read orc file stripes in vectorizedRowBatch

      boolean done = false;
      Long rowCounts = 0L;
      Long rowNumber = oneStripe.getNumberOfRows();

      while(!done) {
        long currentBatchRow = batch.count();

        ColumnVector[] dataVectors = batch.cols;

        for (int cIdx = 0; cIdx < columnNames.length; cIdx++) {   // read one column at a time;
          write1column(dataVectors[cIdx], columnTypes[cIdx].toLowerCase(), cIdx, currentBatchRow, dout);
        }

        rowCounts = rowCounts+currentBatchRow;    // record number of rows of data actually read
        if (rowCounts >= rowNumber)               // read all rows of the stripe already.
          done = true;

        if (!done)  // not done yet, get next batch
          batch = perStripe.nextBatch(batch);
      }

      perStripe.close();
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  /**
   * This method writes one column of H2O data frame at a time.
   *
   * @param oneColumn
   * @param columnType
   * @param cIdx
   * @param rowNumber
     * @param dout
     */
  private static void write1column(ColumnVector oneColumn, String columnType, int cIdx, Long rowNumber,
                                   ParseWriter dout) {
    switch (columnType) {
      case "boolean":
      case "bigint":
      case "int":
      case "smallint":
      case "tinyint":
      case "date":  //FIXME: make sure this is what the customer wants
        writeLongcolumn(oneColumn, columnType, oneColumn.noNulls, oneColumn.isNull, cIdx, rowNumber, dout);
        break;
      case "float":
      case "double":
        writeDoublecolumn(oneColumn, columnType, oneColumn.noNulls, oneColumn.isNull, cIdx, rowNumber, dout);
        break;
      case "string":
      case "varchar":
      case "char":
      case "binary":  //FIXME: only reading it as string right now.
        writeStringcolumn(oneColumn, columnType, oneColumn.noNulls, oneColumn.isNull, cIdx, rowNumber, dout);
        break;
      case "timestamp": //FIXME: read in as a number
        writeTimecolumn(oneColumn, oneColumn.noNulls, oneColumn.isNull, cIdx, rowNumber, dout);
        break;
      case "decimal":   //FIXME: make sure we interpret this correctly, ignore the scale right now
        writeDecimalcolumn(oneColumn, oneColumn.noNulls, oneColumn.isNull, cIdx, rowNumber, dout);
        break;
      default:
        throw new IllegalArgumentException("Unsupported Orc schema type: " + columnType);
    }

  }

  /**
   * This method writes one column of H2O frame for column type timestamp.  This is just a long that
   * records the number of seconds since Jan 1, 2015.
   *
   * @param oneTSColumn
   * @param noNulls
   * @param isNull
   * @param cIdx
   * @param rowNumber
     * @param dout
     */
  private static void writeTimecolumn(ColumnVector oneTSColumn, boolean noNulls, boolean[] isNull, int cIdx,
                                      Long rowNumber, ParseWriter dout) {
    long[] oneColumn = ((TimestampColumnVector) oneTSColumn).time;

    if (noNulls) {
      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
          dout.addNumCol(cIdx, oneColumn[rowIndex]);  // number of seconds since Jan 1, 2015.
      }
    } else {
      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
        if (isNull[rowIndex])
          dout.addInvalidCol(cIdx);
        else
          dout.addNumCol(cIdx, oneColumn[rowIndex]);  // number of seconds since Jan 1, 2015.
      }
    }
  }

  /**
   * This method writes a column to H2O frame for column type Decimal.  It is just written as some
   * integer without using the scale field.  Need to make sure this is what the customer wants.
   *
   * @param oneDecimalColumn
   * @param noNulls
   * @param isNull
   * @param cIdx
   * @param rowNumber
     * @param dout
     */
  private static void writeDecimalcolumn(ColumnVector oneDecimalColumn, boolean noNulls, boolean[] isNull, int cIdx,
                                         Long rowNumber, ParseWriter dout) {
    HiveDecimalWritable[] oneColumn= ((DecimalColumnVector) oneDecimalColumn).vector;

    if (noNulls) {
      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++)
        dout.addNumCol(cIdx, Double.parseDouble(oneColumn[rowIndex].toString()));
    } else {
      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
        if (isNull[rowIndex])
          dout.addInvalidCol(cIdx);
        else
          dout.addNumCol(cIdx, Double.parseDouble(oneColumn[rowIndex].toString()));
      }
    }
  }

  /**
   * This method writes a column of H2O frame for Orc File column types of string, varchar, char and
   * binary at some point.
   *
   * @param oneStringColumn
   * @param columnType
   * @param noNulls
   * @param isNull
   * @param cIdx
   * @param rowNumber
     * @param dout
     */
  private static void writeStringcolumn(ColumnVector oneStringColumn, String columnType, boolean noNulls,
                                        boolean[] isNull, int cIdx, Long rowNumber, ParseWriter dout) {

    byte[][] oneColumn  = ((BytesColumnVector) oneStringColumn).vector;
    int[] stringLength = ((BytesColumnVector) oneStringColumn).length;
    int[] stringStart = ((BytesColumnVector) oneStringColumn).start;

    for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
      if (isNull[rowIndex])
        dout.addInvalidCol(cIdx);
      else {
        BufferedString bs = new BufferedString();
        switch(columnType) {
          case "string":
          case "varchar":
          case "char":
          case "binary":
            byte[] temp = new byte[stringLength[rowIndex]];
            System.arraycopy(oneColumn[rowIndex], stringStart[rowIndex], temp, 0, stringLength[rowIndex]);
            dout.addStrCol(cIdx, bs.set(temp));
        }
      }
    }
  }


  /**
   * This method writes a column of H2O frame for Orc File column type of float or double.
   *
   * @param oneDoubleColumn
   * @param columnType
   * @param noNulls
   * @param isNull
   * @param cIdx
   * @param rowNumber
     * @param dout
     */
  private static void writeDoublecolumn(ColumnVector oneDoubleColumn, String columnType, boolean noNulls,
                                        boolean[] isNull, int cIdx, Long rowNumber, ParseWriter dout) {
    double[] oneColumn = ((DoubleColumnVector) oneDoubleColumn).vector;

    if (noNulls) {
        switch (columnType) {
          case "float":
            for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++)
              dout.addNumCol(cIdx, (float) oneColumn[rowIndex]);
            break;
          case "double":
            for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++)
              dout.addNumCol(cIdx, oneColumn[rowIndex]);
        }
    } else {
      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
        if (isNull[rowIndex])
          dout.addInvalidCol(cIdx);
        else {
          switch (columnType) {
            case "float":
              dout.addNumCol(cIdx, (float) oneColumn[rowIndex]);
              break;
            case "double":
              dout.addNumCol(cIdx, oneColumn[rowIndex]);
          }
        }
      }
    }
  }

  /**
   * This method writes a column of H2O frame for Orc File column type of boolean, bigint, int, smallint,
   * tinyint and date.
   *
   * @param oneLongColumn
   * @param columnType
   * @param noNull
   * @param isNull
   * @param cIdx
   * @param rowNumber
     * @param dout
     */
  private static void writeLongcolumn(ColumnVector oneLongColumn, String columnType, Boolean noNull, boolean[] isNull,
                                      int cIdx, Long rowNumber, ParseWriter dout) {

    long[] oneColumn = ((LongColumnVector) oneLongColumn).vector;

    if (noNull) {
      switch (columnType) {
        case "bigint":
          for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++)
            dout.addNumCol(cIdx, oneColumn[rowIndex]);
          break;
        default:
          for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++)
            dout.addNumCol(cIdx, (int) oneColumn[rowIndex]);
      }
    } else {
      switch (columnType) {
        case "biginit":
          for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
            if (isNull[rowIndex])
              dout.addInvalidCol(cIdx);
            else {
              dout.addNumCol(cIdx, oneColumn[rowIndex]);
            }
          }
          break;
        default:
          for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
            if (isNull[rowIndex])
              dout.addInvalidCol(cIdx);
            else {
              dout.addNumCol(cIdx, (int) oneColumn[rowIndex]);
            }
          }
      }
    }
  }


  public static class OrcParseSetup extends ParseSetup {
    // expand to include Orc specific fields
    final Reader orcFileReader;
    final Long[] cumstripeSizes;   // stripe size, max of all stripe sizes
    final Long totalFileSize;
    final List<StripeInformation> stripesInfo;
    final String[] columnTypesString;
    final Long maxStripeSize;   // biggest stripe size

    public OrcParseSetup(int ncols,
                         String[] columnNames,
                         byte[] ctypes,
                         String[][] domains,
                         String[][] naStrings,
                         String[][] data,
                         Reader orcReader,
                         Long[] allstripes,
                         Long fileSize,
                         List<StripeInformation> stripesInfo,
                         String[] columntypes,
                         Long maxStripeSize) {
      super(OrcParserProvider.ORC_INFO, (byte) '|', true, HAS_HEADER ,
              ncols, columnNames, ctypes, domains, naStrings, data);
      this.orcFileReader = orcReader;
      this.cumstripeSizes = allstripes;
      this.totalFileSize = fileSize;
      this.stripesInfo = stripesInfo;
      this.columnTypesString = columntypes;
      this.maxStripeSize = maxStripeSize;
      // set chunk size to be the max stripe size if the stripe size exceeds the default
      if (this.maxStripeSize > this._chunk_size)  //
        this.setChunkSize(this.maxStripeSize.intValue());
    }

    public OrcParseSetup(ParseSetup ps, Reader reader, Long[] allstripes, Long fileSize,
                         List<StripeInformation> stripesInfo, String[] columnTypes, Long maxStripeSize) {
      super(ps);
      this.orcFileReader = reader;
      this.cumstripeSizes = allstripes;
      this.totalFileSize = fileSize;
      this.stripesInfo = stripesInfo;
      this.columnTypesString = columnTypes;
      this.maxStripeSize = maxStripeSize;

      // set chunk size to be the max stripe size if the stripe size exceeds the default
      if (this.maxStripeSize > this._chunk_size)  //
        this.setChunkSize(this.maxStripeSize.intValue());
    }

    @Override
    protected Parser parser(Key jobKey) {
      return new OrcParser(this, jobKey);
    }

    // this returns a copy of this.cumstripeSizes
    public Long[] getCumstripeSizes() {
      int arrayLength = this.cumstripeSizes.length;

      Long[] tempArray = new Long[arrayLength];
      for (int index = 0; index < arrayLength; index++) {
        tempArray[index] = this.cumstripeSizes[index];
      }

      return tempArray;
    }

    public Long getTotalFileSize() {
      return this.totalFileSize;
    }

    public List<StripeInformation> getStripeInfo() {
      return this.stripesInfo;
    }

    public Reader getOrcFileReader() {
      return this.orcFileReader;
    }

    public String[] getColumnTypesString() {
      return this.columnTypesString;
    }
  }

  public static ParseSetup guessSetup(byte[] bits) {
    try {
      return runOnPreview(bits, new OrcPreviewProcessor<ParseSetup>() {
        @Override
        public ParseSetup process(Reader orcFileReader, StructObjectInspector inspector) {
          return deriveParseSetup(orcFileReader, inspector);
        }
      });
    } catch (IOException e) {
      throw new RuntimeException("Orc format was not recognized", e);
    }

  }

  /** Just like derivedOrcInfo
   */
  static OrcInfo extractOrcInfo(byte[] bits, final ParseSetup requiredSetup) throws IOException {
    return runOnPreview(bits, new OrcPreviewProcessor<OrcInfo>() {
      @Override
      public OrcInfo process(Reader orcReader, StructObjectInspector inspector) {
        OrcParseSetup ps = (OrcParseSetup) deriveParseSetup(orcReader, inspector);

        return new OrcInfo(ps.orcFileReader, ps.cumstripeSizes, ps.totalFileSize, ps.stripesInfo, ps.columnTypesString,
                ps.maxStripeSize);
      }
    });
  }

  /**
   * This method basically grab the reader, the inspector of an orc file.  However, it will
   * return null if an exception was found.
   * @param bits
   * @param processor
   * @param <T>
   * @return
   * @throws IOException
     */
  static <T> T runOnPreview(byte[] bits, OrcPreviewProcessor<T> processor) throws IOException {
    try {
      String tempFile = "tempFile";
      Configuration conf = new Configuration();
      FileUtils.writeByteArrayToFile(new File(tempFile), bits);

      Path p = new Path(tempFile);
      Reader orcFileReader = OrcFile.createReader(p, OrcFile.readerOptions(conf));     // orc reader
      StructObjectInspector insp = (StructObjectInspector) orcFileReader.getObjectInspector();

//      // delete the temp file
//      FileSystem hdfs = FileSystem.get(conf);
//      if (hdfs.exists(p))
//        hdfs.delete(p, false);    // delete the temp file now that we have read it.

      return processor.process(orcFileReader, insp);
    } catch (IOException safeToIgnore) {
      return null;
    }
  }

  /*
   * This function will derive information like column names, types and number from
   * the inspector.
   */
  private static ParseSetup deriveParseSetup(Reader orcFileReader, StructObjectInspector insp) {

    List<StructField> allColumns = (List<StructField>) insp.getAllStructFieldRefs();  // grab column info
    List<StripeInformation> allStripes = orcFileReader.getStripes();  // grab stripe information

    int supportedFieldCnt = 0 ;
    for (StructField oneField:allColumns) {
      if (isSupportedSchema(oneField.getFieldObjectInspector().getTypeName())) supportedFieldCnt++;
    }

    String[] names = new String[supportedFieldCnt];
    byte[] types = new byte[supportedFieldCnt];
    String[][] domains = new String[supportedFieldCnt][];
    String[] dataPreview = new String[supportedFieldCnt];
    String[] dataTypes = new String[supportedFieldCnt];

    // go through all column information
    int columnIndex = 0;

    for (StructField oneField : allColumns) {
      String columnType = oneField.getFieldObjectInspector().getTypeName();
      if (isSupportedSchema(columnType)) {
        names[columnIndex] = oneField.getFieldName();
        types[columnIndex] = schemaToColumnType(columnType);
        dataTypes[columnIndex] = columnType;
//          if (types[columnIndex] == Vec.T_CAT) {  // Orc does not support ENUM/CATEGORICAL
//            domains[columnIndex] = getDomain(schema);
//          }

        columnIndex++;
      } else {
        Log.warn("Skipping field: " + oneField.getFieldName() + " because of unsupported type: " + columnType);
      }
    }

    // get size of each stripe
    Long[] stripeSizes = new Long[allStripes.size()];
    Long fileSize = 0L;
    Long maxStripeSize = 0L;

    for (int index = 0; index < allStripes.size(); index++) {
      Long stripeSize = allStripes.get(index).getDataLength();

      if (stripeSize > maxStripeSize)
        maxStripeSize = stripeSize;

      fileSize = fileSize + stripeSize;
      stripeSizes[index] = fileSize;
    }

    OrcParseSetup ps = new OrcParseSetup(
            supportedFieldCnt,
            names,
            types,
            domains,
            null,
            new String[][] { dataPreview },
            orcFileReader,
            stripeSizes,
            fileSize,
            allStripes,
            dataTypes,
            maxStripeSize
    );
    return ps;
  }

  /** Helper to represent Orc Info
   */
  static class OrcInfo {

    public OrcInfo(Reader orcReader, Long[] allstripes, Long fileSize, List<StripeInformation> stripesInfo,
                   String[] columnTypes, Long bigStripe) {
      this.orcFileReader = orcReader;
      this.cumStripeSizes = allstripes;
      this.totalFileSize = fileSize;
      this.stripesInfo = stripesInfo;
      this.columnTypesString = columnTypes;
      this.maxStripeSize = bigStripe;
    }

    Reader orcFileReader;   // can derive all other fields from here
    Long[] cumStripeSizes;   // information on each stripe
    Long totalFileSize;   // size of all data
    Long maxStripeSize;   // biggest stripe size
    List<StripeInformation> stripesInfo;
    String[] columnTypesString;
  }

  private interface OrcPreviewProcessor<R> {
    R process(Reader orcFileReader, StructObjectInspector inspector);
  }

}
