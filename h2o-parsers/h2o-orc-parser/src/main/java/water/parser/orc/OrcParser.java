package water.parser.orc;

import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.ql.exec.vector.*;
import org.apache.hadoop.hive.ql.io.orc.Reader;
import org.apache.hadoop.hive.ql.io.orc.RecordReader;
import org.apache.hadoop.hive.ql.io.orc.StripeInformation;
import org.apache.hadoop.hive.serde2.io.HiveDecimalWritable;
import org.apache.hadoop.hive.serde2.objectinspector.*;
import org.joda.time.DateTime;
import org.joda.time.MutableDateTime;
import water.Futures;
import water.H2O;
import water.Job;
import water.Key;
import water.fvec.Vec;
import water.parser.*;
import water.util.ArrayUtils;
import water.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
 *
 */
public class OrcParser extends Parser {

  /** Orc Info */
  private final Reader orcFileReader; // can generate all the other fields from this reader
  public static final int DAY_TO_MS = 24*3600*1000;
  public static final int ADD_OFFSET = 8*3600*1000;
  public static final int HOUR_OFFSET = 3600000;  // in ms to offset for leap seconds, years
  private MutableDateTime epoch = new MutableDateTime();  // used to help us out the leap seconds, years
  private ArrayList<String> storeWarnings = new ArrayList<String>();  // store a list of warnings


  OrcParser(ParseSetup setup, Key<Job> jobKey) {
    super(setup, jobKey);

    epoch.setDate(0);   // used to figure out leap seconds, years

    this.orcFileReader = ((OrcParser.OrcParseSetup) setup).orcFileReader;
  }

  private transient int _cidx;

  private transient HashMap<Integer,HashMap<Number,byte[]>> _toStringMaps = new HashMap<>();


  @Override protected ParseWriter streamParse(final InputStream is, final StreamParseWriter dout) throws IOException {
    List<StripeInformation> stripesInfo = ((OrcParseSetup) this._setup).getStripes();
    StreamParseWriter nextChunk = dout;
    Futures fs = new Futures();
    for(int i = 0; i < stripesInfo.size(); i++) {
      parseChunk(i, null, nextChunk);
      nextChunk.close(fs);
      if(dout != nextChunk)
        dout.reduce(nextChunk);
      if(i < stripesInfo.size()-1) nextChunk = nextChunk.nextChunk();
    }
    return dout;
  }

  @Override protected ParseWriter streamParseZip( final InputStream is, final StreamParseWriter dout, InputStream bvs ) throws IOException {
    throw new UnsupportedOperationException("H2O Orc Parser does not support parsing of zipped orc files");
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
    _cidx = chunkId;
    // only do something if within file size and the orc file is not empty
    List<StripeInformation> stripesInfo = ((OrcParseSetup) this._setup).getStripes();
    if(stripesInfo.size() == 0) {
      dout.addError(new ParseWriter.ParseErr("Orc Parser: Empty file.", chunkId, 0L, -2L));
      return dout; // empty file
    }
    OrcParseSetup setup = (OrcParseSetup) this._setup;
    StripeInformation thisStripe = stripesInfo.get(chunkId);  // get one stripe
    // write one stripe of data to H2O frame
    String [] orcTypes = setup.getColumnTypesString();
    boolean[] toInclude = setup.getToInclude();
    try {
      RecordReader perStripe = orcFileReader.rows(thisStripe.getOffset(), thisStripe.getDataLength(),
          setup.getToInclude(), null, setup.getColumnNames());
      VectorizedRowBatch batch = null;
      long rows = 0;
      long rowCount = thisStripe.getNumberOfRows();
      while (rows != rowCount) {
        batch = perStripe.nextBatch(batch);  // read orc file stripes in vectorizedRowBatch
        long currentBatchRow = batch.count();
        int nrows = (int)currentBatchRow;
        if(currentBatchRow != nrows)
          throw new IllegalArgumentException("got batch with too many records, does not fit in int");
        ColumnVector[] dataVectors = batch.cols;
        int colIndex = 0;
        for (int col = 0; col < batch.numCols; ++col) {  // read one column at a time;
          if (toInclude[col + 1]) { // only write a column if we actually want it
            if(_setup.getColumnTypes()[colIndex] != Vec.T_BAD)
              write1column(dataVectors[col], orcTypes[colIndex], colIndex, nrows, dout);
            else dout.addNAs(col,nrows);
            colIndex++;
          }
        }
        rows  += currentBatchRow;    // record number of rows of data actually read
      }
      byte [] col_types = _setup.getColumnTypes();
      for(int i = 0; i < col_types.length; ++i){
        if(col_types[i] == Vec.T_BAD)
          dout.addNAs(i,(int)rowCount);
      }
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
  private void write1column(ColumnVector oneColumn, String columnType, int cIdx, int rowNumber,ParseWriter dout) {
    if(oneColumn.isRepeating && !oneColumn.noNulls) { // ALL NAs
      for(int i = 0; i < rowNumber; ++i)
        dout.addInvalidCol(cIdx);
    } else  switch (columnType.toLowerCase()) {
      case "bigint":
      case "boolean":
      case "int":
      case "smallint":
      case "tinyint":
        writeLongcolumn((LongColumnVector)oneColumn, cIdx, rowNumber, dout);
        break;
      case "float":
      case "double":
        writeDoublecolumn((DoubleColumnVector)oneColumn, cIdx, rowNumber, dout);
        break;
      case "numeric":
      case "real":
        if (oneColumn instanceof LongColumnVector)
          writeLongcolumn((LongColumnVector)oneColumn, cIdx, rowNumber, dout);
        else
          writeDoublecolumn((DoubleColumnVector)oneColumn, cIdx, rowNumber, dout);
        break;
      case "string":
      case "varchar":
      case "char":
//        case "binary":  //FIXME: only reading it as string right now.
        writeStringcolumn((BytesColumnVector)oneColumn, cIdx, rowNumber, dout);
        break;
      case "date":
      case "timestamp":
        writeTimecolumn((LongColumnVector)oneColumn, columnType, cIdx, rowNumber, dout);
        break;
      case "decimal":
        writeDecimalcolumn((DecimalColumnVector)oneColumn, cIdx, rowNumber, dout);
        break;
      default:
        throw new IllegalArgumentException("Unsupported Orc schema type: " + columnType);
    }
  }

  /**
   * This method is written to take care of the leap seconds, leap year effects.  Our original
   * plan of converting number of days from epoch does not quite work out right due to all these
   * leap seconds, years accumulated over the century.  However, I do notice that when we are
   * not correcting for the leap seconds/years, if we build a dateTime object, the hour does not
   * work out to be 00.  Instead it is off.  In this case, we just calculate the offset and take
   * if off our straight forward timestamp calculation.
   *
   * @param daysSinceEpoch: number of days since epoch (1970 1/1)
   * @return long: correct timestamp corresponding to daysSinceEpoch
   */
  private long correctTimeStamp(long daysSinceEpoch) {
    long timestamp = (daysSinceEpoch*DAY_TO_MS+ADD_OFFSET);
    DateTime date = new DateTime(timestamp);
    int hour = date.hourOfDay().get();
    if (hour == 0)
      return timestamp;
    else
      return (timestamp-hour*HOUR_OFFSET);
  }

  /**
   * This method writes one column of H2O frame for column type timestamp.  This is just a long that
   * records the number of seconds since Jan 1, 2015.
   *
   * @param col
   * @param cIdx
   * @param rowNumber
   * @param dout
   */
  private void writeTimecolumn(LongColumnVector col, String columnType,int cIdx,
                               int rowNumber, ParseWriter dout) {
    boolean timestamp = columnType.equals("timestamp");
    long [] oneColumn = col.vector;
    if(col.isRepeating) {
      long val = timestamp ? oneColumn[0] / 1000000 : correctTimeStamp(oneColumn[0]);
      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++)
        dout.addNumCol(cIdx, val, 0);
    } else if(col.noNulls) {
      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++)
        dout.addNumCol(cIdx, timestamp ? oneColumn[rowIndex] / 1000000 : correctTimeStamp(oneColumn[rowIndex]), 0);
    } else {
      boolean[] isNull = col.isNull;
      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
        if (isNull[rowIndex])
          dout.addInvalidCol(cIdx);
        else
          dout.addNumCol(cIdx, timestamp ? oneColumn[rowIndex] / 1000000 : correctTimeStamp(oneColumn[rowIndex]), 0);
      }
    }
  }

  /**
   * This method writes a column to H2O frame for column type Decimal.  It is just written as some
   * integer without using the scale field.  Need to make sure this is what the customer wants.
   *
   * @param col
   * @param cIdx
   * @param rowNumber
   * @param dout
   */
  private void writeDecimalcolumn(DecimalColumnVector col, int cIdx,
                                  int rowNumber, ParseWriter dout) {
    HiveDecimalWritable[] oneColumn = col.vector;
    if(col.isRepeating) {
      HiveDecimal hd = oneColumn[0].getHiveDecimal();
      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++)
        dout.addNumCol(cIdx, hd.unscaledValue().longValue(),-hd.scale());
    } else  if(col.noNulls) {
      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
        HiveDecimal hd = oneColumn[rowIndex].getHiveDecimal();
        dout.addNumCol(cIdx, hd.unscaledValue().longValue(),-hd.scale());
      }
    } else {
      boolean [] isNull = col.isNull;
      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
        if (isNull[rowIndex])
          dout.addInvalidCol(cIdx);
        else {
          HiveDecimal hd = oneColumn[rowIndex].getHiveDecimal();
          dout.addNumCol(cIdx, hd.unscaledValue().longValue(), -hd.scale());
        }
      }
    }
  }

  /**
   * This method writes a column of H2O frame for Orc File column types of string, varchar, char and
   * binary at some point.
   *
   * @param col
   * @param cIdx
   * @param rowNumber
   * @param dout
   */
  private void writeStringcolumn(BytesColumnVector col, int cIdx, int rowNumber, ParseWriter dout) {
    BufferedString bs = new BufferedString();
    if(col.isRepeating) {
      assert col.length[0] >= 0 : getClass().getSimpleName() + ".writeStringcolumn/1: col.length[0]=" + col.length[0] + ", col.start[0]=" + col.start[0];
      dout.addStrCol(cIdx, bs.set(col.vector[0], col.start[0], col.length[0]));
      for (int rowIndex = 1; rowIndex < rowNumber; ++rowIndex)
        dout.addStrCol(cIdx, bs);
    } else if (col.noNulls) {

      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
        int l = col.length[rowIndex];
        assert l >= 0 : getClass().getSimpleName() + ".writeStringcolumn/2: col.col.length[rowIndex]=" + l + ", rowIndex=" + rowIndex;
        dout.addStrCol(cIdx, bs.set(col.vector[rowIndex], col.start[rowIndex], l));
      }

    } else {
      boolean [] isNull = col.isNull;
      for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
        if (isNull[rowIndex])
          dout.addInvalidCol(cIdx);
        else {
          int l = col.length[rowIndex];
          assert l >= 0 : getClass().getSimpleName() + ".writeStringcolumn/3: col.col.length[rowIndex]=" + l + ", rowIndex=" + rowIndex;
          dout.addStrCol(cIdx, bs.set(col.vector[rowIndex], col.start[rowIndex], col.length[rowIndex]));
        }
      }
    }
  }


  /**
   * This method writes a column of H2O frame for Orc File column type of float or double.
   *
   * @param vec
   * @param colId
   * @param rowNumber
   * @param dout
   */
  private void writeDoublecolumn(DoubleColumnVector vec, int colId, int rowNumber, ParseWriter dout) {
    double[] oneColumn = vec.vector;
    byte t = _setup.getColumnTypes()[colId];
    switch(t) {
      case Vec.T_CAT:
        if(_toStringMaps.get(colId) == null)
          _toStringMaps.put(colId,new HashMap<Number, byte[]>());
        HashMap<Number,byte[]> map = _toStringMaps.get(colId);
        BufferedString bs = new BufferedString();
        if(vec.isRepeating) {
          bs.set(StringUtils.toBytes(oneColumn[0]));
          for (int i = 0; i < rowNumber; ++i)
            dout.addStrCol(colId, bs);
        } else  if (vec.noNulls) {
          for (int i = 0; i < rowNumber; i++) {
            double d = oneColumn[i];
            if(map.get(d) == null) // TODO probably more effficient if moved to the data output
              map.put(d, StringUtils.toBytes(d));
            dout.addStrCol(colId, bs.set(map.get(d)));
          }
        } else {
          for (int i = 0; i < rowNumber; i++) {
            boolean [] isNull = vec.isNull;
            if (isNull[i])
              dout.addInvalidCol(colId);
            else {
              double d = oneColumn[i];
              if(map.get(d) == null)
                map.put(d,StringUtils.toBytes(d));
              dout.addStrCol(colId, bs.set(map.get(d)));
            }
          }
        }
        break;
      default:
        if(vec.isRepeating) {
          for (int i = 0; i < rowNumber; ++i)
            dout.addNumCol(colId, oneColumn[0]);
        } else  if (vec.noNulls) {
          for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++)
            dout.addNumCol(colId, oneColumn[rowIndex]);
        } else {
          boolean [] isNull = vec.isNull;
          for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
            if (isNull[rowIndex]) dout.addInvalidCol(colId);
            else dout.addNumCol(colId, oneColumn[rowIndex]);
          }
        }
        break;
    }
  }

  /**
   * This method writes a column of H2O frame for Orc File column type of boolean, bigint, int, smallint,
   * tinyint and date.
   *
   * @param vec
   * @param colId
   * @param rowNumber
   * @param dout
   */
  private void writeLongcolumn(LongColumnVector vec, int colId, int rowNumber, ParseWriter dout) {
    long[] oneColumn = vec.vector;
    byte t = _setup.getColumnTypes()[colId];
    switch(t) {
      case Vec.T_CAT:
        if(_toStringMaps.get(colId) == null)
          _toStringMaps.put(colId,new HashMap<Number, byte[]>());
        HashMap<Number,byte[]> map = _toStringMaps.get(colId);
        BufferedString bs = new BufferedString();
        if(vec.isRepeating) {
          bs.set(StringUtils.toBytes(oneColumn[0]));
          for (int i = 0; i < rowNumber; ++i)
            dout.addStrCol(colId, bs);
        } else  if (vec.noNulls) {
          for (int i = 0; i < rowNumber; i++) {
            long l = oneColumn[i];
            if(map.get(l) == null)
              map.put(l,StringUtils.toBytes(l));
            dout.addStrCol(colId, bs.set(map.get(l)));
          }
        } else {
          for (int i = 0; i < rowNumber; i++) {
            boolean [] isNull = vec.isNull;
            if (isNull[i])
              dout.addInvalidCol(colId);
            else {
              long l = oneColumn[i];
              if(map.get(l) == null)
                map.put(l,StringUtils.toBytes(l));
              dout.addStrCol(colId, bs.set(map.get(l)));
            }
          }
        }
        break;
      default:
        if(vec.isRepeating) {
          for (int i = 0; i < rowNumber; ++i)
            dout.addNumCol(colId, oneColumn[0], 0);
        } else  if (vec.noNulls) {
          for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
            check_Min_Value(oneColumn[rowIndex], colId, rowNumber, dout);
            dout.addNumCol(colId, oneColumn[rowIndex], 0);
          }
        } else {
          for (int rowIndex = 0; rowIndex < rowNumber; rowIndex++) {
            boolean [] isNull = vec.isNull;
            if (isNull[rowIndex])
              dout.addInvalidCol(colId);
            else {
              check_Min_Value(oneColumn[rowIndex], colId, rowNumber, dout);
              dout.addNumCol(colId, oneColumn[rowIndex], 0);
            }
          }
        }
        break;
    }
  }

  /**
   * This method is written to check and make sure any value written to a column of type long
   * is more than Long.MIN_VALUE.  If this is not true, a warning will be passed to the user.
   *
   * @param l
   * @param cIdx
   * @param rowNumber
   * @param dout
   */
  private void check_Min_Value(long l, int cIdx, int rowNumber, ParseWriter dout) {
    if (l <= Long.MIN_VALUE) {
      String warning = "Orc Parser: Long.MIN_VALUE: " + l + " is found in column "+cIdx+" row "+rowNumber +
          " of stripe "+_cidx +".  This value is used for sentinel and will not be parsed correctly.";
      dout.addError(new ParseWriter.ParseErr(warning, _cidx, rowNumber, -2L));
    }
  }

  public static class OrcParseSetup extends ParseSetup {
    // expand to include Orc specific fields
    transient Reader orcFileReader;
    String[] columnTypesString;
    boolean[] toInclude;
    String[] allColumnNames;

    public OrcParseSetup(int ncols,
                         String[] columnNames,
                         byte[] ctypes,
                         String[][] domains,
                         String[][] naStrings,
                         String[][] data,
                         Reader orcReader,
                         String[] columntypes,
                         boolean[] toInclude,
                         String[] allColNames, ParseWriter.ParseErr[] errs) {
      super(OrcParserProvider.ORC_INFO, (byte) '|', true, HAS_HEADER ,
          ncols, columnNames, ctypes, domains, naStrings, data, errs);
      this.orcFileReader = orcReader;
      this.columnTypesString = columntypes;
      this.toInclude = toInclude;
      int[] skippedColumns = this.getSkippedColumns();
      if (skippedColumns != null) {
        for (int cindex:skippedColumns)
          this.toInclude[cindex]=false; // set skipped columns to be false in order not to read it in.
      }
      this.allColumnNames = allColNames;
    }

    @Override
    protected boolean isCompatible(ParseSetup setupB) {
      return super.isCompatible(setupB) && Arrays.equals(getColumnTypes(),setupB.getColumnTypes());
    }

    @Override
    protected Parser parser(Key jobKey) {
      return new OrcParser(this, jobKey);
    }

    public Reader getOrcFileReader() {
      return this.orcFileReader;
    }

    public String[] getColumnTypesString() {
      return this.columnTypesString;
    }

    public void setColumnTypeStrings(String[] columnTypeStrings) {
      this.columnTypesString = columnTypeStrings;
    }

    public boolean[] getToInclude() { return this.toInclude; }
    public String[] getAllColNames() { return this.allColumnNames; }
    public void setAllColNames(String[] columnNames) {
      this.allColumnNames = allColumnNames;
    }

    public void setOrcFileReader(Reader orcFileReader) {
      this.orcFileReader = orcFileReader;
      this.stripesInfo = orcFileReader.getStripes();
    }
    private transient List<StripeInformation> stripesInfo;
    public List<StripeInformation> getStripes() {return stripesInfo;}
  }

  // types are flattened in pre-order tree walk, here we just count the number of fields for non-primitve types
  // which are ignored for now
  static private int countStructFields(ObjectInspector x, ArrayList<String> allColumnNames) {
    int res = 1;
    switch(x.getCategory()) {
      case STRUCT:
        StructObjectInspector structObjectInspector = (StructObjectInspector) x;
        List<StructField> allColumns = (List<StructField>) structObjectInspector.getAllStructFieldRefs(); // column info
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
    ParseWriter.ParseErr[] errs = new ParseWriter.ParseErr[0];

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
        errs = ArrayUtils.append(errs, new ParseWriter.ParseErr("Orc Parser: Skipping field: "
            + oneField.getFieldName() + " because of unsupported type: " + columnType, -1, -1L, -2L));
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
        dataTypes,
        toInclude,
        allNames,
        errs
    );

    return ps;
  }
}
