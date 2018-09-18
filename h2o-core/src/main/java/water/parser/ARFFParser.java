package water.parser;

import java.util.ArrayList;

import water.Key;
import water.fvec.ByteVec;
import water.fvec.Vec;

import static water.parser.DefaultParserProviders.ARFF_INFO;

class ARFFParser extends CsvParser {
  private static final String TAG_ATTRIBUTE = "@ATTRIBUTE";
  private static final byte GUESS_SEP = ParseSetup.GUESS_SEP;

  ARFFParser(ParseSetup ps, Key jobKey) { super(ps, jobKey); }

  /** Try to parse the bytes as ARFF format  */
  static ParseSetup guessSetup(ByteVec bv, byte[] bits, byte sep, boolean singleQuotes, String[] columnNames, String[][] naStrings) {
    if (columnNames != null) throw new UnsupportedOperationException("ARFFParser doesn't accept columnNames.");

    // Parse all lines starting with @ until EOF or @DATA
    boolean haveData = false;
    String[][] data = new String[0][];
    String[] labels;
    String[][] domains;
    String[] headerlines = new String[0];
    byte[] ctypes;

    // header section
    ArrayList<String> header = new ArrayList<>();

    int offset;
    int chunk_idx = 0; //relies on the assumption that bits param have been extracted from first chunk: cf. ParseSetup#map
    String last_line_fragment = null;
    do {
      offset = readArffHeader(0, header, bits, singleQuotes, last_line_fragment);
      if (offset > bits.length) {
        last_line_fragment = header.remove(header.size() - 1);
        bits = bv.chunkForChunkIdx(++chunk_idx).getBytes();
      } else {
        last_line_fragment = null;
      }
    } while (last_line_fragment != null);

    if (offset < bits.length && !CsvParser.isEOL(bits[offset]))
      haveData = true; //more than just the header

    if (header.size() == 0)
      throw new ParseDataset.H2OParseException("No data!");

    headerlines = header.toArray(headerlines);

    // process header
    final int nlines = headerlines.length;
    int ncols = nlines;
    labels = new String[ncols];
    domains = new String[ncols][];
    ctypes = new byte[ncols];
    processArffHeader(ncols, headerlines, labels, domains, ctypes);

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
        throw new ParseDataset.H2OParseException("Unexpected line.");
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
            throw new ParseDataset.H2OParseException("Failed to detect separator.");
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
    return new ParseSetup(ARFF_INFO, sep, singleQuotes, ParseSetup.NO_HEADER, ncols, labels, ctypes, domains, naStrings, data);
  }

  private static int readArffHeader(int offset, ArrayList<String> header, byte[] bits, boolean singleQuotes, String line_fragment) {
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
        if (line_fragment != null) {
          str = line_fragment + str;
          line_fragment = null;
        }
        String[] tok = determineTokens(str, CHAR_SPACE, singleQuotes);
        if (tok.length > 0 && tok[0].equalsIgnoreCase("@RELATION")) continue; // Ignore name of dataset
        if (!str.isEmpty()) header.add(str);
      }
    }
    return offset;
  }

  static void processArffHeader(int ncols, String[] headerlines, String[] labels, String[][] domains, byte[] ctypes) {
    for (int i=0; i<ncols; ++i) {
      String[] line = headerlines[i].split("\\s+", 2);
      if (!line[0].equalsIgnoreCase(TAG_ATTRIBUTE)) {
        throw new ParseDataset.H2OParseException("Expected line to start with @ATTRIBUTE.");
      } else {
        final String spec = (line.length == 2) ? line[1].replaceAll("\\s", " ") : ""; // normalize separators
        int sepIdx = spec.lastIndexOf(' ');
        if (sepIdx < 0) {
          throw new ParseDataset.H2OParseException("Expected @ATTRIBUTE to be followed by <attribute-name> <datatype>");
        }
        final String type = spec.substring(sepIdx + 1).trim();
        domains[i] = null;
        ctypes[i] = Vec.T_BAD;
        if (type.equalsIgnoreCase("NUMERIC") || type.equalsIgnoreCase("REAL") || type.equalsIgnoreCase("INTEGER") || type.equalsIgnoreCase("INT")) {
          ctypes[i] = Vec.T_NUM;
        }
        else if (type.equalsIgnoreCase("DATE") || type.equalsIgnoreCase("TIME")) {
          ctypes[i] = Vec.T_TIME;
        }
        else if (type.equalsIgnoreCase("ENUM")) {
          ctypes[i] = Vec.T_CAT;
        }
        else if (type.equalsIgnoreCase("STRING")) {
          ctypes[i] = Vec.T_STR;
        }
        else if (type.equalsIgnoreCase("UUID")) { //extension of ARFF
          ctypes[i] = Vec.T_UUID;
        }
        else if (type.equalsIgnoreCase("RELATIONAL")) {
          throw new UnsupportedOperationException("Relational ARFF format is not supported.");
        }
        else if (type.endsWith("}")) {
          int domainSpecStart = spec.lastIndexOf('{');
          if (domainSpecStart < 0)
            throw new ParseDataset.H2OParseException("Invalid type specification.");
          sepIdx = domainSpecStart - 1;
          String domainSpec = spec.substring(domainSpecStart + 1, line[1].length() - 1);
          domains[i] = domainSpec.split(",");
          for (int j = 0; j < domains[i].length; j++)
            domains[i][j] = domains[i][j].trim();
          if (domains[i][0].length() > 0)
            ctypes[i] = Vec.T_CAT; // case of {A,B,C} (valid list of factors)
        }

        if (ctypes[i] == Vec.T_BAD)
          throw new ParseDataset.H2OParseException("Unexpected line, type not recognized. Attribute specification: " + type);

        // remove the whitespaces separating the label and the type specification
        while ((sepIdx > 0) && (spec.charAt(sepIdx - 1) == ' ')) sepIdx--;
        String label = line[1].substring(0, sepIdx); // use the raw string before whitespace normalization

        // remove quotes
        if (label.length() >= 2 && label.startsWith("'") && label.endsWith("'"))
          label = label.substring(1, label.length() - 1);

        labels[i] = label;
      }
    }

  }
}
