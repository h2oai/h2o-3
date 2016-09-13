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
import water.parser.ParseWriter;

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

  private final ParseWriter _writer;
  private final Converter[] _converters;

  private int _currentRecordIdx = -1;

  ChunkConverter(MessageType parquetSchema, byte[] chunkSchema, ParseWriter writer) {
    _writer = writer;
    int colIdx = 0;
    _converters = new Converter[chunkSchema.length];
    for (Type parquetField : parquetSchema.getFields()) {
      assert parquetField.isPrimitive();
      _converters[colIdx] = newConverter(colIdx, chunkSchema[colIdx], parquetField.asPrimitiveType());
      colIdx++;
    }
  }

  @Override
  public Converter getConverter(int fieldIndex) {
    return _converters[fieldIndex];
  }

  @Override
  public void start() {
    _currentRecordIdx++;
  }

  @Override
  public void end() {
    _writer.newLine();
    assert _writer.lineNum() - 1 == _currentRecordIdx;
  }

  int getCurrentRecordIdx() {
    return _currentRecordIdx;
  }

  private PrimitiveConverter newConverter(int colIdx, byte vecType, PrimitiveType parquetType) {
    switch (vecType) {
      case Vec.T_BAD:
      case Vec.T_CAT:
      case Vec.T_STR:
      case Vec.T_UUID:
      case Vec.T_TIME:
        boolean dictSupport = parquetType.getOriginalType() == OriginalType.UTF8 || parquetType.getOriginalType() == OriginalType.ENUM;
        return new StringConverter(_writer, colIdx, dictSupport);
      case Vec.T_NUM:
        return new NumberConverter(colIdx, _writer);
      default:
        throw new UnsupportedOperationException("Unsupported type " + vecType);
    }
  }

  private static class StringConverter extends PrimitiveConverter {

    private final BufferedString _bs = new BufferedString();
    private final int _colIdx;
    private final ParseWriter _writer;
    private final boolean _dictionarySupport;
    private String[] _dict;

    StringConverter(ParseWriter writer, int colIdx, boolean dictionarySupport) {
      _colIdx = colIdx;
      _writer = writer;
      _dictionarySupport = dictionarySupport;
    }

    @Override
    public void addBinary(Binary value) {
      _bs.set(value.toStringUsingUTF8().getBytes());
      _writer.addStrCol(_colIdx, _bs);
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
      _bs.set(_dict[dictionaryId].getBytes());
      _writer.addStrCol(_colIdx, _bs);
    }
  }

  private static class NumberConverter extends PrimitiveConverter {

    private final int _colIdx;
    private final ParseWriter _writer;
    private final BufferedString _bs = new BufferedString();

    NumberConverter(int _colIdx, ParseWriter _writer) {
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
      _bs.set(value.toStringUsingUTF8().getBytes());
      _writer.addStrCol(_colIdx, _bs);
    }
  }

}
