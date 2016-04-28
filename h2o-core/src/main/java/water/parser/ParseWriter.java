package water.parser;

import water.Freezable;
import water.Iced;

/** Interface for writing results of parsing, accumulating numbers and
 *  strings or handling invalid lines & parse errors.  */
public interface ParseWriter extends Freezable {

  class ParseErr extends Iced {
    public ParseErr(){}
    public ParseErr(String err, int cidx, long lineNum, long byteOff){
      _err = err;
      _cidx = cidx;
      _lineNum = lineNum;
      _byteOffset = byteOff;
    }
    // as recorded during parsing
    String _file = "unknown";
    String _err = "unknown";
    long _byteOffset = -1;
    int _cidx = -1;
    long _lineNum = -1;
    // filled int he end (when we now the line-counts)
    long _gLineNum = -1;
    public String toString(){
      return "ParseError at file " + _file + (_gLineNum == -1?"":" at line " + _lineNum + " ( destination line " + _gLineNum + " )") + "  at byte offset " + _byteOffset + "; error = \'" + _err + "\'";
    }
  }

  void setColumnNames(String [] names);
  // Register a newLine from the parser
  void newLine();
  // True if already forced into a string column (skip number parsing)
  boolean isString(int colIdx);
  // Add a number column with given digits & exp
  void addNumCol(int colIdx, long number, int exp);
  // Add a number column with given digits & exp
  void addNumCol(int colIdx, double d);
  // An an invalid / missing entry
  void addInvalidCol(int colIdx);
  // Add a String column
  void addStrCol( int colIdx, BufferedString str );
  // Final rolling back of partial line
  void rollbackLine();
  // ignore (and report the error) the rest of the line
  void invalidLine(ParseErr err);
  // report an error (e.g. invalid number)
  void addError(ParseErr err);
  void setIsAllASCII(int colIdx, boolean b);
  boolean hasErrors();
  ParseErr [] removeErrors();
  long lineNum();

}
