package water.parser.orc;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.ql.exec.vector.*;
import org.apache.hadoop.hive.ql.io.orc.*;
import org.apache.hadoop.hive.serde2.io.HiveDecimalWritable;
import org.apache.hadoop.hive.serde2.objectinspector.*;
import water.H2O;
import water.Iced;
import water.Job;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.*;
import water.persist.PersistHdfs;
import water.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
  private BufferedString bs = new BufferedString();

  OrcParser(ParseSetup setup, Key<Job> jobKey) {
    super(setup, jobKey);
    this.orcFileReader = ((OrcParser.OrcParseSetup) setup).orcFileReader;
  }

  /**
   * This method calculates the number of stripes that will be read for each chunk.  Since
   * only single threading is supported in reading each stripe, we will never split one stripe
   * over different chunks.
   *
   * @param chunkId: chunk index, calculated as file size/chunk size.  The file size is calculated
   *            with data plus overhead in terms of headers and other info, number of chunks
   *            calculated will be higher than the actual chunks needed.  If the chunk number
   *            is too high, the method will return without writing to
   *            dout.
   * @param din: ParseReader, not used for parsing orc files
   * @param dout: ParseWriter, used to add data to H2O frame.
     * @return: Parsewriter dout.
     */
  @Override
  protected final ParseWriter parseChunk(int chunkId, ParseReader din, ParseWriter dout) {
    // only do something if within file size and the orc file is not empty
    StripeInformation [] stripesInfo = ((OrcParseSetup) this._setup).getStripeInfo();
    if(stripesInfo.length == 0) return dout; // empty file
    OrcParseSetup setup = (OrcParseSetup) this._setup;
    StripeInformation thisStripe = stripesInfo[chunkId];  // get one stripe
    // write one stripe of data to H2O frame
    final String [] columnNames = setup.getColumnNames();
    final int ncols = columnNames.length;
    String [] orcTypes = setup.getColumnTypesString();
    try {
      RecordReader perStripe = orcFileReader.rows(thisStripe.getOffset(), thisStripe.getDataLength(), setup.getToInclude(), null, setup.getColumnNames());
      VectorizedRowBatch batch = perStripe.nextBatch(null);  // read orc file stripes in vectorizedRowBatch
      boolean done = false;
      long rowCounts = 0L;
      long rowNumber = thisStripe.getNumberOfRows();
      while (!done) {
        long currentBatchRow = batch.count();
        ColumnVector[] dataVectors = batch.cols;
        for (int col = 0; col < ncols; ++col)    // read one column at a time;
          write1column(dataVectors[col], orcTypes[col], col, currentBatchRow, dout);
        rowCounts = rowCounts + currentBatchRow;    // record number of rows of data actually read
        if (rowCounts >= rowNumber)               // read all rows of the stripe already.
          done = true;
        if (!done)  // not done yet, get next batch
          batch = perStripe.nextBatch(batch);
      }
      assert rowCounts == rowNumber:"rowCounts = " + rowCounts + ", rowNumber = " + rowNumber;
      perStripe.close();
    } catch(IOException ioe) {
      throw new RuntimeException(ioe);
    }
    return dout;
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
  private void write1column(ColumnVector oneColumn, String columnType, int cIdx, Long rowNumber,
                                   ParseWriter dout) {
    try {
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
    } catch(Throwable t ) {
      t.printStackTrace();

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
  private void writeTimecolumn(ColumnVector oneTSColumn, boolean noNulls, boolean[] isNull, int cIdx,
                                      Long rowNumber, ParseWriter dout) {
    long[] oneColumn = ((LongColumnVector) oneTSColumn).vector;

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
  private void writeDecimalcolumn(ColumnVector oneDecimalColumn, boolean noNulls, boolean[] isNull, int cIdx,
                                         Long rowNumber, ParseWriter dout) {
    HiveDecimalWritable[] oneColumn = ((DecimalColumnVector) oneDecimalColumn).vector;
    for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
      HiveDecimal hd = oneColumn[rowIndex].getHiveDecimal();
      if(noNulls || !isNull[rowIndex])
        dout.addNumCol(cIdx, hd.unscaledValue().longValue(),-hd.scale());
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
  private void writeStringcolumn(ColumnVector oneStringColumn, String columnType, boolean noNulls,
                                        boolean[] isNull, int cIdx, Long rowNumber, ParseWriter dout) {

    byte[][] oneColumn  = ((BytesColumnVector) oneStringColumn).vector;
    int[] stringLength = ((BytesColumnVector) oneStringColumn).length;
    int[] stringStart = ((BytesColumnVector) oneStringColumn).start;

    for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
      if (isNull[rowIndex])
        dout.addInvalidCol(cIdx);
      else {
        dout.addStrCol(cIdx, bs.set(oneColumn[rowIndex],stringStart[rowIndex],stringLength[rowIndex]));
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
  private void writeDoublecolumn(ColumnVector oneDoubleColumn, String columnType, boolean noNulls,
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
  private void writeLongcolumn(ColumnVector oneLongColumn, String columnType, boolean noNull, boolean[] isNull,
                                      int cIdx, Long rowNumber, ParseWriter dout) {
    long[] oneColumn = ((LongColumnVector) oneLongColumn).vector;
    if (noNull) {
      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++)
        dout.addNumCol(cIdx, oneColumn[rowIndex],0);
    } else {
      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
        if (isNull[rowIndex])
          dout.addInvalidCol(cIdx);
        else
          dout.addNumCol(cIdx, oneColumn[rowIndex],0);
      }
    }
  }


  public static class IcedStripeInfo extends Iced implements StripeInformation{
    long _off;
    long _len;
    long _row;
    long _dlen;
    long _indexLen;
    long _flen;

    public IcedStripeInfo(StripeInformation si) {
      _off = si.getOffset();
      _len = si.getLength();
      _row = si.getNumberOfRows();
      _dlen = si.getDataLength();
      _indexLen = si.getIndexLength();
      _flen = si.getFooterLength();
    }
    @Override public long getOffset() {return  _off;}
    @Override public long getLength() {return _len;}
    @Override public long getNumberOfRows() {return _row;}
    @Override public long getIndexLength() { return _indexLen;}
    @Override public long getDataLength() {return _dlen;}
    @Override public long getFooterLength() {return _flen;}
  }

  public static class OrcParseSetup extends ParseSetup {
    // expand to include Orc specific fields
    transient Reader orcFileReader;
    long[] cumstripeSizes;   // stripe size, max of all stripe sizes
    long totalFileSize;
    IcedStripeInfo [] stripesInfo;
    String[] columnTypesString;
    long maxStripeSize;   // biggest stripe size
    boolean[] toInclude;
    String[] allColumnNames;

    public OrcParseSetup(int ncols,
                         String[] columnNames,
                         byte[] ctypes,
                         String[][] domains,
                         String[][] naStrings,
                         String[][] data,
                         Reader orcReader,
                         long[] allstripes,
                         long fileSize,
                         List<StripeInformation> stripesInfo,
                         String[] columntypes,
                         long maxStripeSize,
                         boolean[] toInclude,
                         String[] allColNames) {
      super(OrcParserProvider.ORC_INFO, (byte) '|', true, HAS_HEADER ,
              ncols, columnNames, ctypes, domains, naStrings, data);
      this.orcFileReader = orcReader;
      this.cumstripeSizes = allstripes;
      this.totalFileSize = fileSize;
      this.stripesInfo = new IcedStripeInfo[stripesInfo.size()];
      for(int i = 0; i < this.stripesInfo.length; ++i)
        this.stripesInfo[i] = new IcedStripeInfo(stripesInfo.get(i));
      this.columnTypesString = columntypes;
      this.maxStripeSize = maxStripeSize;
      this.toInclude = toInclude;
      this.allColumnNames = allColNames;
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

    public StripeInformation [] getStripeInfo() {return this.stripesInfo;}

    public Reader getOrcFileReader() {
      return this.orcFileReader;
    }

    public String[] getColumnTypesString() {
      return this.columnTypesString;
    }

    public boolean[] getToInclude() { return this.toInclude; }
    public String[] getAllColNames() { return this.allColumnNames; }

    public void setOrcFileReader(Reader orcFileReader) {
      this.orcFileReader = orcFileReader;
    }
  }


//  /**
//   * This method basically grab the reader, the inspector of an orc file.  However, it will
//   * return null if an exception was found.
//   * @param bits
//   * @param processor
//   * @param <T>
//   * @return
//   * @throws IOException
//     */
//  static <T> T runOnPreview(byte[] bits, OrcPreviewProcessor<T> processor) throws IOException {
//    try {
//      String tempFile = "tempFile";
//      Configuration conf = new Configuration();
//      FileUtils.writeByteArrayToFile(new File(tempFile), bits);
//
//      Path p = new Path(tempFile);
//      Reader orcFileReader = OrcFile.createReader(p, OrcFile.readerOptions(conf));     // orc reader
//      StructObjectInspector insp = (StructObjectInspector) orcFileReader.getObjectInspector();
//
//      return processor.process(orcFileReader, insp);
//    } catch (IOException safeToIgnore) {
//      return null;
//    }
//  }

  // types are flattened in pre-order tree walk, here we just count the number of fields for non-primitve types which are ignored for now
  static private int countStructFields(ObjectInspector x, ArrayList<String> allColumnNames) {
    int res = 1;
    switch(x.getCategory()) {
      case STRUCT:
        StructObjectInspector structObjectInspector = (StructObjectInspector) x;
        List<StructField> allColumns = (List<StructField>) structObjectInspector.getAllStructFieldRefs();  // grab column info
        for (StructField oneField : allColumns) {
          allColumnNames.add(oneField.getFieldName());
          res += countStructFields(oneField.getFieldObjectInspector(),allColumnNames);
        }
        break;
      case LIST:
        ListObjectInspector listObjectInspector = (ListObjectInspector) x;
        allColumnNames.add("list");
        res += countStructFields(listObjectInspector.getListElementObjectInspector(),allColumnNames);
        break;
      case MAP:
        MapObjectInspector mapObjectInspector = (MapObjectInspector) x;
        allColumnNames.add("mapKey");
        res += countStructFields(mapObjectInspector.getMapKeyObjectInspector(),allColumnNames);
        allColumnNames.add("mapValue");
        res += countStructFields(mapObjectInspector.getMapValueObjectInspector(),allColumnNames);
        break;
      case UNION:
        UnionObjectInspector unionObjectInspector = (UnionObjectInspector)x;
        allColumnNames.add("union");
        for( ObjectInspector xx:unionObjectInspector.getObjectInspectors())
          res += countStructFields(xx,allColumnNames);
        break;
      case PRIMITIVE:break;
      default: throw H2O.unimpl();
    }
    return res;
  }
  /*
   * This function will derive information like column names, types and number from
   * the inspector.
   */
  static OrcParseSetup deriveParseSetup(Reader orcFileReader, StructObjectInspector insp) {
    List<StructField> allColumns = (List<StructField>) insp.getAllStructFieldRefs();  // grab column info
    List<StripeInformation> allStripes = orcFileReader.getStripes();  // grab stripe information
    ArrayList<String> allColNames = new ArrayList<>();
    boolean[] toInclude = new boolean[allColumns.size()+1];
    int supportedFieldCnt = 0 ;
    int colIdx = 0;
    for (StructField oneField:allColumns) {
      allColNames.add(oneField.getFieldName());
      String columnType = oneField.getFieldObjectInspector().getTypeName();
      if (columnType.toLowerCase().contains("decimal")) {
        columnType = "decimal";
      }
      if (isSupportedSchema(columnType)) {
        toInclude[colIdx+1] = true;
        supportedFieldCnt++;
      }
      int cnt = countStructFields(oneField.getFieldObjectInspector(),allColNames);
      if(cnt > 1)
        toInclude = Arrays.copyOf(toInclude,toInclude.length + cnt-1);
      colIdx+=cnt;
    }
    String [] allNames = allColNames.toArray(new String[allColNames.size()]);
    String[] names = new String[supportedFieldCnt];

    byte[] types = new byte[supportedFieldCnt];
    String[][] domains = new String[supportedFieldCnt][];
    String[] dataPreview = new String[supportedFieldCnt];
    String[] dataTypes = new String[supportedFieldCnt];

    // go through all column information
    int columnIndex = 0;
    for (StructField oneField : allColumns) {
      String columnType = oneField.getFieldObjectInspector().getTypeName();
      if (columnType.toLowerCase().contains("decimal"))
        columnType = "decimal"; // get rid of strange attachment
      if (isSupportedSchema(columnType)) {
        names[columnIndex] = oneField.getFieldName();
        types[columnIndex] = schemaToColumnType(columnType);
        dataTypes[columnIndex] = columnType;
        columnIndex++;
      } else {
        Log.warn("Skipping field: " + oneField.getFieldName() + " because of unsupported type: " + columnType);
      }
    }

    // get size of each stripe
    long[] stripeSizes = new long[allStripes.size()];
    long fileSize = 0L;
    long maxStripeSize = 0L;

    for (int index = 0; index < allStripes.size(); index++) {
      long stripeSize = allStripes.get(index).getDataLength();

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
            maxStripeSize,
            toInclude,
            allNames
    );
    return ps;
  }
}
