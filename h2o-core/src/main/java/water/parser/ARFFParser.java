package water.parser;

import java.util.ArrayList;
import java.util.List;

import water.Key;
import water.exceptions.H2OUnsupportedDataFileException;
import water.fvec.ByteVec;
import water.fvec.Vec;
import water.util.ArrayUtils;

import static water.parser.DefaultParserProviders.ARFF_INFO;

class ARFFParser extends CsvParser {
  private static final String INCOMPLETE_HEADER = "@H20_INCOMPLETE_HEADER@";
  private static final String SKIP_NEXT_HEADER = "@H20_SKIP_NEXT_HEADER@";
  private static final String TAG_ATTRIBUTE = "@ATTRIBUTE";
  private static final String NA = "?"; //standard NA in Arff format
  private static final byte GUESS_SEP = ParseSetup.GUESS_SEP;
  private static final byte[] NON_DATA_LINE_MARKERS = {'%', '@'};

  ARFFParser(ParseSetup ps, Key jobKey) { super(ps, jobKey); }

  @Override
  protected byte[] nonDataLineMarkers() {
    return NON_DATA_LINE_MARKERS;
  }

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

    int offset = 0;
    int chunk_idx = 0; //relies on the assumption that bits param have been extracted from first chunk: cf. ParseSetup#map
    boolean readHeader = true;
    while (readHeader) {
      offset = readArffHeader(0, header, bits, singleQuotes);
      if (isValidHeader(header)) {
        String lastHeader = header.get(header.size() - 1);
        if (INCOMPLETE_HEADER.equals(lastHeader) || SKIP_NEXT_HEADER.equals(lastHeader)) {
          bits = bv.chunkForChunkIdx(++chunk_idx).getBytes();
          continue;
        }
      } else if (chunk_idx > 0) { //first chunk parsed correctly, but not the next => formatting issue
        throw new H2OUnsupportedDataFileException(
            "Arff parsing: Invalid header. If compressed file, please try without compression",
            "First chunk was parsed correctly, but a following one failed, common with archives as only first chunk in decompressed");
      }
      readHeader = false;
    }

    if (offset < bits.length && !CsvParser.isEOL(bits[offset]))
      haveData = true; //more than just the header

    if (header.size() == 0)
      throw new ParseDataset.H2OParseException("No data!");

    headerlines = header.toArray(headerlines);

    // process header
    int ncols = headerlines.length;
    labels = new String[ncols];
    domains = new String[ncols][];
    ctypes = new byte[ncols];
    processArffHeader(ncols, headerlines, labels, domains, ctypes);

    // data section (for preview)
    if (haveData) {
      final int preview_max_length = 10;
      ArrayList<String> datablock = new ArrayList<>();
      //Careful! the last data line could be incomplete too (cf. readArffHeader)
      while (offset < bits.length && datablock.size() < preview_max_length) {
        int lineStart = offset;
        while (offset < bits.length && !CsvParser.isEOL(bits[offset])) ++offset;
        int lineEnd = offset;
        ++offset;
        // For Windoze, skip a trailing LF after CR
        if ((offset < bits.length) && (bits[offset] == CsvParser.CHAR_LF)) ++offset;
        if (ArrayUtils.contains(NON_DATA_LINE_MARKERS, bits[lineStart])) continue;
        if (lineEnd > lineStart) {
          String str = new String(bits, lineStart, lineEnd - lineStart).trim();
          if (!str.isEmpty()) datablock.add(str);
        }
      }
      if (datablock.size() == 0)
        throw new ParseDataset.H2OParseException("Unexpected line.");

      // process data section
      String[] datalines = datablock.toArray(new String[datablock.size()]);
      data = new String[datalines.length][];

      // First guess the field separator by counting occurrences in first few lines
      if (datalines.length == 1) {
        if (sep == GUESS_SEP) {
          //could be a bit more robust than just counting commas?
          if (datalines[0].split(",").length > 2) sep = ',';
          else if (datalines[0].split(" ").length > 2) sep = ' ';
          else throw new ParseDataset.H2OParseException("Failed to detect separator.");
        }
        data[0] = determineTokens(datalines[0], sep, singleQuotes);
        ncols = (ncols > 0) ? ncols : data[0].length;
        labels = null;
      } else {                    // 2 or more lines
        if (sep == GUESS_SEP) {   // first guess the separator
          //FIXME if last line is incomplete, this logic fails
          sep = guessSeparator(datalines[0], datalines[1], singleQuotes);
          if (sep == GUESS_SEP && datalines.length > 2) {
            sep = guessSeparator(datalines[1], datalines[2], singleQuotes);
            if (sep == GUESS_SEP) sep = guessSeparator(datalines[0], datalines[2], singleQuotes);
          }
          if (sep == GUESS_SEP) sep = (byte) ' '; // Bail out, go for space
        }

        for (int i = 0; i < datalines.length; ++i) {
          data[i] = determineTokens(datalines[i], sep, singleQuotes);
        }
      }
    }

    naStrings = addDefaultNAs(naStrings, ncols);

    // Return the final setup
    return new ParseSetup(ARFF_INFO, sep, singleQuotes, ParseSetup.NO_HEADER, ncols, labels, ctypes, domains, naStrings, data);
  }

  private static String[][] addDefaultNAs(String[][] naStrings, int nCols) {
    final String[][] nas = naStrings == null ? new String[nCols][] : naStrings;
    for (int i = 0; i < nas.length; i++) {
      String [] colNas = nas[i];
      if (!ArrayUtils.contains(colNas, NA)) {
        nas[i] = colNas = ArrayUtils.append(colNas, NA);
      }
    }
    return nas;
  }

  private static boolean isValidHeader(List<String> header) {
    for (String line : header) {
      if (!isValidHeaderLine(line)) return false;
    }
    return header.size() > 0;
  }

  private static boolean isValidHeaderLine(String str) {
    return str != null && str.startsWith("@");
  }

  private static int readArffHeader(int offset, List<String> header, byte[] bits, boolean singleQuotes) {
    String lastHeader = header.size() > 0 ? header.get(header.size() - 1) : null;
    boolean lastHeaderIncomplete = INCOMPLETE_HEADER.equals(lastHeader);
    boolean skipFirstLine = SKIP_NEXT_HEADER.equals(lastHeader);
    if (lastHeaderIncomplete || skipFirstLine) header.remove(header.size() - 1);  //remove fake header
    lastHeader = lastHeaderIncomplete ? header.remove(header.size() - 1) : null; //remove incomplete header for future concatenation

    while (offset < bits.length) {
      int lineStart = offset;
      while (offset < bits.length && !CsvParser.isEOL(bits[offset])) ++offset;
      int lineEnd = offset;

      ++offset;
      // For Windoze, skip a trailing LF after CR
      if ((offset < bits.length) && (bits[offset] == CsvParser.CHAR_LF)) ++offset;

      boolean lastLineIncomplete = lineEnd == bits.length && !CsvParser.isEOL(bits[lineEnd-1]);

      if (skipFirstLine) {
        skipFirstLine = false;
        if (lastLineIncomplete) header.add(SKIP_NEXT_HEADER);
        continue;
      }

      if (bits[lineStart] == '%') { //skip comment lines
        if (!lastHeaderIncomplete) {
          if (lastLineIncomplete) header.add(SKIP_NEXT_HEADER);
          continue;
        }
      }

      String str = new String(bits, lineStart, lineEnd - lineStart).trim();
      if (lastHeaderIncomplete) {
          str = lastHeader + str;  //add current line portion to last header portion from previous chunk
          lastHeaderIncomplete = false;
      } else if (str.matches("(?i)^@relation\\s?.*$")) { //ignore dataset name
        continue;
      } else if (str.matches("(?i)^@data\\s?.*$")) {  //stop header parsing as soon as we encounter data
        break;
      }
      if (!str.isEmpty()) {
        header.add(str);
        if (lastLineIncomplete) header.add(INCOMPLETE_HEADER);
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
