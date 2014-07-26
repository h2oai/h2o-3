package water.parser;

import java.util.ArrayList;

class ARFFParser extends CsvParser {
  private static final byte AUTO_SEP = ParseSetup.AUTO_SEP;
  ARFFParser(ParseSetup ps) { super(ps); }

  /** Try to parse the bytes as ARFF format  */
  static ParseSetup guessSetup( byte[] bits, byte sep, int ncols, boolean singleQuotes, int checkHeader, String[] columnNames ) {
    if (columnNames != null) throw new UnsupportedOperationException("ARFFParser doesn't accept columnNames.");
    final byte single_quote = singleQuotes ? CsvParser.CHAR_SINGLE_QUOTE : -1;

    // Parse all lines starting with @ until EOF or @DATA
    boolean have_data = false;
    int offset = 0;
    String[][] data;
    String[] labels;
    String[][] domains;
    String[] headerlines = new String[0];
    byte[] ctypes;

    // header section
    {
      ArrayList<String> header = new ArrayList<>();
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
          if (str.equalsIgnoreCase("@DATA")) {
            if (!CsvParser.isEOL(bits[offset])) {
              have_data = true; //more than just the header
            }
            break;
          }
          String[] tok = determineTokens(str, CHAR_SPACE, single_quote);
          if (tok[0].equalsIgnoreCase("@RELATION")) continue; // Ignore name of dataset
          if (!str.isEmpty()) header.add(str);
        }
      }
      if (header.size() == 0)
        return new ParseSetup(false, 0, header.size(), new String[]{"No data!"}, ParserType.AUTO, AUTO_SEP, 0, false, null, null, null, checkHeader, null);
      headerlines = header.toArray(headerlines);

      // process header
      int nlines = headerlines.length;
      ncols = nlines;
      data = new String[ncols][];
      labels = new String[ncols];
      domains = new String[ncols][];
      ctypes = new byte[ncols];
      for (int i=0; i<ncols; ++i) {
        data[i] = headerlines[i].split("\\s+");
        if (!data[i][0].equalsIgnoreCase("@ATTRIBUTE")) {
          return new ParseSetup(false,1,nlines,new String[]{"Expected line to start with @ATTRIBUTE."},ParserType.ARFF,AUTO_SEP,ncols,singleQuotes,null,null,data,checkHeader, null);
        } else {
          if (data[i].length != 3 ) {
            return new ParseSetup(false,1,nlines,new String[]{"Expected @ATTRIBUTE to be followed by <attribute-name> <datatype>"},ParserType.ARFF,AUTO_SEP,ncols,singleQuotes,null,null,data,checkHeader, null);
          }
          labels[i] = data[i][1];
          String type = data[i][2];
          domains[i] = null;
          if (type.equalsIgnoreCase("NUMERIC") || type.equalsIgnoreCase("REAL") || type.equalsIgnoreCase("INTEGER") || type.equalsIgnoreCase("INT")) {
            ctypes[i] = ParseDataset2.FVecDataOut.NCOL;
            continue;
          }
          else if (type.equalsIgnoreCase("DATE") || type.equalsIgnoreCase("TIME")) {
            ctypes[i] = ParseDataset2.FVecDataOut.TCOL;
            continue;
          }
          else if (type.equalsIgnoreCase("ENUM")) {
            ctypes[i] = ParseDataset2.FVecDataOut.ECOL;
            continue;
          }
          else if (type.equalsIgnoreCase("STRING")) {
//            ctypes[i] = ParseDataset2.FVecDataOut.SCOL; //FIXME: enable this once implemented
            ctypes[i] = ParseDataset2.FVecDataOut.ECOL;
            continue;
          }
          else if (type.equalsIgnoreCase("UUID")) { //extension of ARFF
            ctypes[i] = ParseDataset2.FVecDataOut.ICOL;
            continue;
          }
          else if (type.equalsIgnoreCase("RELATIONAL")) {
            throw new UnsupportedOperationException("Relational ARFF format is not supported.");
          }
          else if (type.startsWith("{") && type.endsWith("}")) {
            domains[i] = data[i][2].replaceAll("[{}]", "").split(",");
            if (domains[i][0].length() > 0) {
              // case of {A,B,C} (valid list of factors)
              ctypes[i] = ParseDataset2.FVecDataOut.ECOL;
              continue;
            }
          }

          // only get here if data is invalid ARFF
          return new ParseSetup(false,1,nlines,new String[]{"Unexpected line."},ParserType.ARFF,AUTO_SEP,ncols,singleQuotes,null,null,data,checkHeader, null);
        }
      }
    }

    // data section (for preview)
    if (have_data) {
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
        return new ParseSetup(false, 0, headerlines.length, new String[]{"No data!"}, ParserType.AUTO, AUTO_SEP, 0, false, null, null, null, checkHeader, null);
      datalines = datablock.toArray(datalines);

      // process data section
      int nlines = datalines.length;
      data = new String[nlines][];

      // First guess the field separator by counting occurrences in first few lines
      if (nlines == 1) {
        if (sep == AUTO_SEP) {
          if (datalines[0].split(",").length > 2) sep = (byte) ',';
          else if (datalines[0].split(" ").length > 2) sep = ' ';
          else
            return new ParseSetup(false, 1, 0, new String[]{"Failed to guess separator."}, ParserType.CSV, AUTO_SEP, ncols, singleQuotes, null, null, data, checkHeader, null);
        }
        data[0] = determineTokens(datalines[0], sep, single_quote);
        ncols = (ncols > 0) ? ncols : data[0].length;
        if (checkHeader == 0) labels = ParseSetup.allStrings(data[0]) ? data[0] : null;
        else if (checkHeader == 1) labels = data[0];
        else labels = null;
      } else {                    // 2 or more lines
        if (sep == AUTO_SEP) {   // first guess the separator
          sep = guessSeparator(datalines[0], datalines[1], single_quote);
          if (sep == AUTO_SEP && nlines > 2) {
            if (sep == AUTO_SEP) sep = guessSeparator(datalines[1], datalines[2], single_quote);
            if (sep == AUTO_SEP) sep = guessSeparator(datalines[0], datalines[2], single_quote);
          }
          if (sep == AUTO_SEP) sep = (byte) ' '; // Bail out, go for space
        }

        for (int i = 0; i < datalines.length; ++i) {
          data[i] = determineTokens(datalines[i], sep, single_quote);
        }
      }
    }

    // Return the final setup
    return new ParseSetup( true, 0, headerlines.length, null, ParserType.ARFF, sep, ncols, singleQuotes, labels, domains, data, checkHeader, ctypes);
  }

}
