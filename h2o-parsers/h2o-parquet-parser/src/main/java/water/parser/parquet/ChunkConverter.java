package water.parser.parquet;

import org.apache.parquet.column.Dictionary;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.io.api.Converter;
import org.apache.parquet.io.api.GroupConverter;
import org.apache.parquet.io.api.PrimitiveConverter;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.OriginalType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.StringUtils;

/**
 * Implementation of Parquet's GroupConverter for H2O's chunks.
 *
 * ChunkConverter is responsible for converting parquet data into Chunks. As opposed to regular
 * Parquet converters this converter doesn't actually produce any records and instead writes the data
 * using a provided ParseWriter to chunks. The (artificial) output of the converter is number of
 * the record that was written to the chunk.
 *
 * Note: It is meant to be used as a root converter.
 */
class ChunkConverter extends GroupConverter {

  private final WriterDelegate _writer; // this guy actually performs the writing.
  private final Converter[] _converters;
  private final boolean[] _keepColumns;

  private long _currentRecordIdx = -1;

  ChunkConverter(MessageType parquetSchema, byte[] chunkSchema, WriterDelegate writer, boolean[] keepcolumns) {
    _writer = writer;
    _keepColumns = keepcolumns;

    int colIdx = 0; // index to columns actually parsed
    _converters = new Converter[chunkSchema.length];
    int trueColumnIndex = 0;  // count all columns including the skipped ones
    for (Type parquetField : parquetSchema.getFields()) {
      assert parquetField.isPrimitive();
      if (_keepColumns[trueColumnIndex]) {
        _converters[trueColumnIndex] = newConverter(colIdx, chunkSchema[trueColumnIndex], parquetField.asPrimitiveType());
        colIdx++;
      } else {
        _converters[trueColumnIndex] = nullConverter(chunkSchema[trueColumnIndex], parquetField.asPrimitiveType());
      }

      trueColumnIndex++;
    }
  }

  @Override
  public Converter getConverter(int fieldIndex) {
    return _converters[fieldIndex];
  }

  @Override
  public void start() {
    _currentRecordIdx++;
    _writer.startLine();
  }

  @Override
  public void end() {
    _writer.endLine();
  }

  long getCurrentRecordIdx() {
    return _currentRecordIdx;
  }

  private PrimitiveConverter nullConverter(byte vecType, PrimitiveType parquetType) {
    switch (vecType) {
      case Vec.T_BAD:
      case Vec.T_CAT:
      case Vec.T_STR:
      case Vec.T_UUID:
      case Vec.T_TIME:
      case Vec.T_NUM:
          boolean dictSupport = parquetType.getOriginalType() == OriginalType.UTF8 || parquetType.getOriginalType() == OriginalType.ENUM;
          return new NullStringConverter(dictSupport);
      default:
        throw new UnsupportedOperationException("Unsupported type " + vecType);
    }
  }

  private static class NullStringConverter extends PrimitiveConverter {
    private final boolean _dictionarySupport;

    NullStringConverter(boolean dictionarySupport) {
      _dictionarySupport = dictionarySupport;
    }

    @Override
    public void addBinary(Binary value) { ; }

    @Override
    public boolean hasDictionarySupport() {
      return _dictionarySupport;
    }

    @Override
    public void setDictionary(Dictionary dictionary) {
    }

    @Override
    public void addValueFromDictionary(int dictionaryId) {
    }

    @Override
    public void addBoolean(boolean value) { }

    @Override
    public void addDouble(double value) { }

    @Override
    public void addFloat(float value) { }

    @Override
    public void addInt(int value) { }

    @Override
    public void addLong(long value) { }
  }

  private PrimitiveConverter newConverter(int colIdx, byte vecType, PrimitiveType parquetType) {
    switch (vecType) {
      case Vec.T_BAD:
      case Vec.T_CAT:
      case Vec.T_STR:
      case Vec.T_UUID:
      case Vec.T_TIME:
        if (OriginalType.TIMESTAMP_MILLIS.equals(parquetType.getOriginalType()) || parquetType.getPrimitiveTypeName().equals(PrimitiveType.PrimitiveTypeName.INT96)) {
          return new TimestampConverter(colIdx, _writer);
        } else {
          boolean dictSupport = parquetType.getOriginalType() == OriginalType.UTF8 || parquetType.getOriginalType() == OriginalType.ENUM;
          return new StringConverter(_writer, colIdx, dictSupport);
        }
      case Vec.T_NUM:
        if (OriginalType.DECIMAL.equals(parquetType.getOriginalType()))
          return new DecimalConverter(colIdx, parquetType.getDecimalMetadata().getScale(), _writer);
        else
          return new NumberConverter(colIdx, _writer);
      default:
        throw new UnsupportedOperationException("Unsupported type " + vecType);
    }
  }

  private static class StringConverter extends PrimitiveConverter {
    private final BufferedString _bs = new BufferedString();
    private final int _colIdx;
    private final WriterDelegate _writer;
    private final boolean _dictionarySupport;
    private String[] _dict;

    StringConverter(WriterDelegate writer, int colIdx, boolean dictionarySupport) {
      _colIdx = colIdx;
      _writer = writer;
      _dictionarySupport = dictionarySupport;
    }

    @Override
    public void addBinary(Binary value) {
      writeStrCol(StringUtils.bytesOf(value.toStringUsingUTF8()));
    }

    @Override
    public boolean hasDictionarySupport() {
      return _dictionarySupport;
    }

    @Override
    public void setDictionary(Dictionary dictionary) {
      _dict = new String[dictionary.getMaxId() + 1];
      for (int i = 0; i <= dictionary.getMaxId(); i++) {
        _dict[i] = dictionary.decodeToBinary(i).toStringUsingUTF8();
      }
    }

    @Override
    public void addValueFromDictionary(int dictionaryId) {
      writeStrCol(StringUtils.bytesOf(_dict[dictionaryId]));
    }

    private void writeStrCol(byte[] data) {
      _bs.set(data);
      _writer.addStrCol(_colIdx, _bs);
    }

  }

  private static class NumberConverter extends PrimitiveConverter {

    private final int _colIdx;
    private final WriterDelegate _writer;
    private final BufferedString _bs = new BufferedString();

    NumberConverter(int _colIdx, WriterDelegate _writer) {
      this._colIdx = _colIdx;
      this._writer = _writer;
    }

    @Override
    public void addBoolean(boolean value) {
      _writer.addNumCol(_colIdx, value ? 1 : 0);
    }

    @Override
    public void addDouble(double value) {
      _writer.addNumCol(_colIdx, value);
    }

    @Override
    public void addFloat(float value) {
      _writer.addNumCol(_colIdx, value);
    }

    @Override
    public void addInt(int value) {
      _writer.addNumCol(_colIdx, value, 0);
    }

    @Override
    public void addLong(long value) {
      _writer.addNumCol(_colIdx, value, 0);
    }

    @Override
    public void addBinary(Binary value) {
      _bs.set(StringUtils.bytesOf(value.toStringUsingUTF8()));
      _writer.addStrCol(_colIdx, _bs);
    }
  }

  private static class DecimalConverter extends PrimitiveConverter {

    private final int _colIdx;
    private final WriterDelegate _writer;
    private final BufferedString _bs = new BufferedString();
    private final int _exp;

    DecimalConverter(int colIdx, int scale, WriterDelegate writer) {
      _colIdx = colIdx;
      _writer = writer;
      _exp = -scale;
    }

    @Override
    public void addBoolean(boolean value) {
      throw new UnsupportedOperationException("Boolean type is not supported by DecimalConverter");
    }

    @Override
    public void addDouble(double value) {
      throw new UnsupportedOperationException("Double type is not supported by DecimalConverter");
    }

    @Override
    public void addFloat(float value) {
      throw new UnsupportedOperationException("Float type is not supported by DecimalConverter");
    }

    @Override
    public void addInt(int value) {
      _writer.addNumCol(_colIdx, value, _exp);
    }

    @Override
    public void addLong(long value) {
      _writer.addNumCol(_colIdx, value, _exp);
    }

    @Override
    public void addBinary(Binary value) {
      throw new UnsupportedOperationException("Arbitrary precision Decimal type is currently not supported by H2O." +
              "Please use 64-bit decimal type instead (precision <= 18).");
    }
  }

  private static class TimestampConverter extends PrimitiveConverter {

    private final int _colIdx;
    private final WriterDelegate _writer;

    TimestampConverter(int _colIdx, WriterDelegate _writer) {
      this._colIdx = _colIdx;
      this._writer = _writer;
    }

    @Override
    public void addLong(long value) {
      _writer.addNumCol(_colIdx, value, 0);
    }

    @Override
    public void addBinary(Binary value) {
      final long timestampMillis = ParquetInt96TimestampConverter.getTimestampMillis(value);

      _writer.addNumCol(_colIdx, timestampMillis);
    }
  }

}
