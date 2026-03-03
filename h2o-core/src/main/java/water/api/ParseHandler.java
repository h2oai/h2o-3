package water.api;

import water.DKV;
import water.Key;
import water.api.schemas3.JobV3;
import water.api.schemas3.ParseSVMLightV3;
import water.api.schemas3.ParseV3;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ParseHandler extends Handler {
  // Entry point for parsing.
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ParseV3 parse(int version, ParseV3 parse) {
    ParserInfo parserInfo = ParserService.INSTANCE.getByName(parse.parse_type).info();
    ParseSetup setup = new ParseSetup(parserInfo,
                                      parse.separator, parse.single_quotes,
                                      parse.check_header, parse.number_columns,
                                      delNulls(parse.column_names),
                                      ParseSetup.strToColumnTypes(parse.column_types),
                                      parse.domains, parse.na_strings,
                                      null,
                                      new ParseWriter.ParseErr[0], parse.chunk_size,
                                      parse.decrypt_tool != null ? parse.decrypt_tool.key() : null, parse.skipped_columns,
                                      parse.custom_non_data_line_markers != null ? parse.custom_non_data_line_markers.getBytes(): null,
                                      parse.escapechar, parse.force_col_types, parse.tz_adjust_to_local);

    if (parse.source_frames == null)
      throw new H2OIllegalArgumentException("Data for Frame '" + parse.destination_frame.name + "' is not available. Please check that the path is valid (for all H2O nodes).'");
    Key[] srcs = new Key[parse.source_frames.length];
    for (int i = 0; i < parse.source_frames.length; i++) {
      srcs[i] = parse.source_frames[i].key();
    }

    if (parse.partition_by != null) {
      final String[][] partitionValues = syntheticColumValuesFromPartitions(parse.partition_by, srcs);
      setup.setSyntheticColumns(parse.partition_by, partitionValues, Vec.T_CAT);
    }

    if ((setup.getParseType().name().toLowerCase().equals("svmlight") ||
            (setup.getParseType().name().toLowerCase().equals("avro") ))
            && ((setup.getSkippedColumns() != null) && (setup.getSkippedColumns().length >0)))
      throw new H2OIllegalArgumentException("Parser: skipped_columns are not supported for SVMlight or Avro parsers.");

    if (setup.getSkippedColumns() !=null &&
            ((setup.get_parse_columns_indices()==null) || (setup.get_parse_columns_indices().length==0)))
      throw new H2OIllegalArgumentException("Parser:  all columns in the file are skipped and no H2OFrame" +
              " can be returned."); // Need this to send error message to R
    
    if (parse.force_col_types && parse.column_types != null)
      setup.setOrigColumnTypes(parse.column_types);
    
    parse.job = new JobV3(ParseDataset.parse(
            parse.destination_frame.key(), srcs, parse.delete_on_done, setup, parse.blocking
    )._job);
    if (parse.blocking) {
      Frame fr = DKV.getGet(parse.destination_frame.key());
      parse.rows = fr.numRows();
    }

    return parse;
  }

  /**
   * Extracts synthetic column values from the keys of parsed files, as the keys contain path to the file and the
   * partitioned file path contains the values necessary for all the columns the dataset is partitioned by.
   *
   * @param partitionColumnNames Names of the columns to be partitioned. Those are expected to be validated in
   *                             the ParseSetup phase.
   * @param fileKeys             Keys to all the files parsed, with key IDs having the file path in them.
   * @return A two-dimensional {@link String} array, where the first dimension is the index of the column equal to the
   * partitionColumnNames. The second dimension are the categorical values the datasaet has been partitioned by.
   * @throws IllegalArgumentException If one of the partitioned columns is not found in any of the paths provided.
   */
  private static String[][] syntheticColumValuesFromPartitions(final String[] partitionColumnNames, final Key[] fileKeys)
          throws IllegalArgumentException {
    final String[][] values = new String[fileKeys.length][partitionColumnNames.length];
    for (int fileIndex = 0; fileIndex < fileKeys.length; fileIndex++) {

      for (int partitionIndex = 0; partitionIndex < partitionColumnNames.length; partitionIndex++) {
        final Matcher matcher = Pattern.compile(partitionColumnNames[partitionIndex] + "=([^\\/\\\\]+)")
                .matcher(fileKeys[fileIndex].toString());
        if (!matcher.find()) {
          throw new IllegalArgumentException(String.format("Unable to find partition column '%s' in file key '%s'",
                  partitionColumnNames[partitionIndex], fileKeys[fileIndex].toString()));
        }
        final String partitionValue = matcher.group(1);
        values[fileIndex][partitionIndex] = partitionValue;
      }
    }
    return values;
  }

  private static String[] delNulls(String[] names) {
    if (names == null) return null;
    for(int i=0; i < names.length; i++)
      if (names[i].equals("null")) names[i] = null;
    return names;
  }

  @SuppressWarnings("unused")  // called through reflection by RequestServer
  public JobV3 parseSVMLight(int version, ParseSVMLightV3 parse) {
    Key [] fkeys = new Key[parse.source_frames.length];
    for(int i = 0; i < fkeys.length; ++i)
      fkeys[i] = parse.source_frames[i].key();
    Key<Frame> destKey = parse.destination_frame == null? null : parse.destination_frame.key();
    if(destKey == null)
      destKey = Key.make(ParseSetup.createHexName(parse.source_frames[0].toString()));
    ParseSetup setup = ParseSetup.guessSetup(fkeys,ParseSetup.makeSVMLightSetup());
    return new JobV3().fillFromImpl(ParseDataset.forkParseSVMLight(destKey,fkeys,setup));
  }

}
