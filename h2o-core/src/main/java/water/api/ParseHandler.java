package water.api;

import water.DKV;
import water.Key;
import water.api.schemas3.JobV3;
import water.api.schemas3.ParseSVMLightV3;
import water.api.schemas3.ParseV3;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.parser.*;

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
                                      parse.decrypt_tool != null ? parse.decrypt_tool.key() : null, parse.skipped_columns);

    if (parse.source_frames == null) throw new H2OIllegalArgumentException("Data for Frame '" + parse.destination_frame.name + "' is not available. Please check that the path is valid (for all H2O nodes).'");
    Key[] srcs = new Key[parse.source_frames.length];
    for (int i = 0; i < parse.source_frames.length; i++)
      srcs[i] = parse.source_frames[i].key();

    if ((setup.getParseType().name().toLowerCase().equals("svmlight") ||
            (setup.getParseType().name().toLowerCase().equals("avro") ))
            && ((setup.getSkippedColumns() != null) && (setup.getSkippedColumns().length >0)))
      throw new H2OIllegalArgumentException("Parser: skipped_columns are not supported for SVMlight or Avro parsers.");

    if (setup.getSkippedColumns() !=null &&
            ((setup.get_parse_columns_indices()==null) || (setup.get_parse_columns_indices().length==0)))
      throw new H2OIllegalArgumentException("Parser:  all columns in the file are skipped and no H2OFrame" +
              " can be returned."); // Need this to send error message to R

    parse.job = new JobV3(ParseDataset.parse(
            parse.destination_frame.key(), srcs, parse.delete_on_done, setup, parse.blocking
    )._job);
    if (parse.blocking) {
      Frame fr = DKV.getGet(parse.destination_frame.key());
      parse.rows = fr.numRows();
    }

    return parse;
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
