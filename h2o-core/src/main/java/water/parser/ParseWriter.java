package water.parser;

import water.Freezable;

/** Interface for writing results of parsing, accumulating numbers and
 *  strings (enums) or handling invalid lines & parse errors.  */
interface ParseWriter extends Freezable {
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
  void invalidLine(String err);
  void setIsAllASCII(int colIdx, boolean b);
}
