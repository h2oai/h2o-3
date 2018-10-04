package water.parser.parquet;

import water.DKV;
import water.Iced;
import water.Key;
import water.parser.BufferedString;
import water.parser.ParseWriter;
import water.util.IcedInt;
import water.util.Log;

import java.util.Arrays;

import static water.H2OConstants.MAX_STR_LEN;

final class WriterDelegate {

  private final int _maxStringSize;
  private final int[] _colRawSize; // currently only used for String columns
  private final int _numCols;

  private ParseWriter _writer;
  private int _col;

  WriterDelegate(ParseWriter writer, int numCols) {
    _maxStringSize = getMaxStringSize();
    _numCols = numCols;
    _colRawSize = new int[numCols];
    setWriter(writer);
  }

  // For unit tests only: allows to set maximum string size in a test for all nodes
  private int getMaxStringSize() {
    Iced<?> maxSize = DKV.getGet(Key.make(WriterDelegate.class.getCanonicalName() + "_maxStringSize"));
    return (maxSize instanceof IcedInt) ? ((IcedInt) maxSize)._val : MAX_STR_LEN;
  }

  void startLine() {
    _col = -1;
  }

  void endLine() {
    moveToCol(_numCols);
    _writer.newLine();
  }

  private int moveToCol(int colIdx) {
    for (int c = _col + 1; c < colIdx; c++) _writer.addInvalidCol(c);
    _col = colIdx;
    return _col;
  }

  void addNumCol(int colIdx, long number, int exp) {
    _writer.addNumCol(moveToCol(colIdx), number, exp);
  }

  void addNumCol(int colIdx, double d) {
    _writer.addNumCol(moveToCol(colIdx), d);
  }

  void addStrCol(int colIdx, BufferedString str) {
    if (_colRawSize[colIdx] == -1)
      return; // already exceeded max length

    long totalSize = (long) str.length() + _colRawSize[colIdx];
    if (totalSize > _maxStringSize) {
      _colRawSize[colIdx] = -1;
      Log.err("Total String size limit reached: skipping remaining values in column: " + colIdx + "!");
      return;
    }

    _colRawSize[colIdx] += str.length();
    _writer.addStrCol(moveToCol(colIdx), str);
  }

  long lineNum() {
    return _writer.lineNum();
  }

  final void setWriter(ParseWriter writer) {
    _writer = writer;
    _col = Integer.MIN_VALUE;
    Arrays.fill(_colRawSize, 0);
  }

}
