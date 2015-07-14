package water.parser;

import java.util.ArrayList;

import water.exceptions.H2OParseSetupException;
import water.fvec.Vec;

class ARFFParser extends CsvParser {
  private static final byte GUESS_SEP = ParseSetup.GUESS_SEP;

  ARFFParser(ParseSetup ps) { super(ps); }

  /** Try to parse the bytes as ARFF format  */
  static ParseSetup guessSetup(byte[] bits, byte sep, boolean singleQuotes, String[] columnNames, String[][] naStrings) {
    if (columnNames != null) throw new UnsupportedOperationException("ARFFParser doesn't accept columnNames.");

    // Parse all lines starting with @ until EOF or @DATA
    boolean haveData = false;
    int offset = 0;
    String[][] data;
    String[] labels;
    String[][] domains;
    String[] headerlines = new String[0];
    byte[] ctypes;

    // header section
    ArrayList<String> header = new ArrayList<>();
    offset = readArffHeader(offset, header, bits, singleQuotes);
    if (offset < bits.length && !CsvParser.isEOL(bits[offset]))
      haveData = true; //more than just the header

    if (header.size() == 0)
      throw new H2OParseSetupException("No data!");
    headerlines = header.toArray(headerlines);

    // process header
    final int nlines = headerlines.length;
    int ncols = nlines;
    data = new String[ncols][];
    labels = new String[ncols];
    domains = new String[ncols][];
    ctypes = new byte[ncols];
    processArffHeader(ncols, headerlines, data, labels, domains, ctypes);

    // data section (for preview)
    if (haveData) {
      String[] datalines = new String[0];
      ArrayList<String> datablock = new ArrayList<>();
      while (offset < bits.length) {
        int lineStart = offset;
        while (offset < bits.length && !CsvParser.isEOL(bits[offset])) ++offset;
        int lineEnd = offset;
        ++offset;
        // For Windoze, skip a trailing LF after CR
        if ((offset < bits.length) && (bits[offset] == CsvParser.CHAR_LF)) ++offset;
        if (bits[lineStart] == '#') continue; // Ignore      comment lines
        if (bits[lineStart] == '%') continue; // Ignore ARFF comment lines
        if (lineEnd > lineStart) {
          String str = new String(bits, lineStart, lineEnd - lineStart).trim();
          if (!str.isEmpty()) datablock.add(str);
        }
      }
      if (datablock.size() == 0)
        throw new H2OParseSetupException("Unexpected line.");
      datalines = datablock.toArray(datalines);

      // process data section
      int nlines2 = Math.min(10, datalines.length);
      data = new String[nlines2][];

      // First guess the field separator by counting occurrences in first few lines
      if (nlines2 == 1) {
        if (sep == GUESS_SEP) {
          if (datalines[0].split(",").length > 2) sep = (byte) ',';
          else if (datalines[0].split(" ").length > 2) sep = ' ';
          else
            throw new H2OParseSetupException("Failed to detect separator.");
        }
        data[0] = determineTokens(datalines[0], sep, singleQuotes);
        ncols = (ncols > 0) ? ncols : data[0].length;
        labels = null;
      } else {                    // 2 or more lines
        if (sep == GUESS_SEP) {   // first guess the separator
          sep = guessSeparator(datalines[0], datalines[1], singleQuotes);
          if (sep == GUESS_SEP && nlines2 > 2) {
            sep = guessSeparator(datalines[1], datalines[2], singleQuotes);
            if (sep == GUESS_SEP) sep = guessSeparator(datalines[0], datalines[2], singleQuotes);
          }
          if (sep == GUESS_SEP) sep = (byte) ' '; // Bail out, go for space
        }

        for (int i = 0; i < nlines2; ++i) {
          data[i] = determineTokens(datalines[i], sep, singleQuotes);
        }
      }
    }

    // Return the final setup
    return new ParseSetup(ParserType.ARFF, sep, singleQuotes, ParseSetup.NO_HEADER, ncols, labels, ctypes, domains, naStrings, data);
  }

  private static int readArffHeader(int offset, ArrayList<String> header, byte[] bits, boolean singleQuotes) {
    while (offset < bits.length) {
      int lineStart = offset;
      while (offset < bits.length && !CsvParser.isEOL(bits[offset])) ++offset;
      int lineEnd = offset;
      ++offset;
      // For Windoze, skip a trailing LF after CR
      if ((offset < bits.length) && (bits[offset] == CsvParser.CHAR_LF)) ++offset;
      if (bits[lineStart] == '#') continue; // Ignore      comment lines
      if (bits[lineStart] == '%') continue; // Ignore ARFF comment lines
      if (lineEnd > lineStart) {
        if (bits[lineStart] == '@' &&
                (bits[lineStart+1] == 'D' || bits[lineStart+1] =='d' ) &&
                (bits[lineStart+2] == 'A' || bits[lineStart+2] =='a' ) &&
                (bits[lineStart+3] == 'T' || bits[lineStart+3] =='t' ) &&
                (bits[lineStart+4] == 'A' || bits[lineStart+4] =='a' )){
          break;
        }
        String str = new String(bits, lineStart, lineEnd - lineStart).trim();
        String[] tok = determineTokens(str, CHAR_SPACE, singleQuotes);
        if (tok.length > 0 && tok[0].equalsIgnoreCase("@RELATION")) continue; // Ignore name of dataset
        if (!str.isEmpty()) header.add(str);
      }
    }
    return offset;
  }

  private static void processArffHeader(int ncols, String[] headerlines, String[][] data, String[] labels, String[][] domains, byte[] ctypes) {
    for (int i=0; i<ncols; ++i) {
      data[i] = headerlines[i].split("\\s+");
      if (!data[i][0].equalsIgnoreCase("@ATTRIBUTE")) {
        throw new H2OParseSetupException("Expected line to start with @ATTRIBUTE.");
      } else {
        if (data[i].length < 3 ) {
          throw new H2OParseSetupException("Expected @ATTRIBUTE to be followed by <attribute-name> <datatype>");
        }
        labels[i] = data[i][1];
        String type = data[i][2];
        domains[i] = null;
        if (type.equalsIgnoreCase("NUMERIC") || type.equalsIgnoreCase("REAL") || type.equalsIgnoreCase("INTEGER") || type.equalsIgnoreCase("INT")) {
          ctypes[i] = Vec.T_NUM;
          continue;
        }
        else if (type.equalsIgnoreCase("DATE") || type.equalsIgnoreCase("TIME")) {
          ctypes[i] = Vec.T_TIME;
          continue;
        }
        else if (type.equalsIgnoreCase("ENUM")) {
          ctypes[i] = Vec.T_ENUM;
          continue;
        }
        else if (type.equalsIgnoreCase("STRING")) {
          ctypes[i] = Vec.T_STR;
          continue;
        }
        else if (type.equalsIgnoreCase("UUID")) { //extension of ARFF
          ctypes[i] = Vec.T_UUID;
          continue;
        }
        else if (type.equalsIgnoreCase("RELATIONAL")) {
          throw new UnsupportedOperationException("Relational ARFF format is not supported.");
        }
        else if (type.startsWith("{") && data[i][data[i].length-1].endsWith("}")) {
          StringBuilder builder = new StringBuilder();
          for(int j = 2; j < data[i].length; j++) {
            builder.append(data[i][j]);
          }
          domains[i] = builder.toString().replaceAll("[{}]", "").split(",");
          if (domains[i][0].length() > 0) {
            // case of {A,B,C} (valid list of factors)
            ctypes[i] = Vec.T_ENUM;
            continue;
          }
        }

        // only get here if data is invalid ARFF
        throw new H2OParseSetupException("Unexpected line.");
      }
    }

  }
}
