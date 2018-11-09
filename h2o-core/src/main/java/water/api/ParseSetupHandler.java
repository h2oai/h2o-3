package water.api;

import water.DKV;
import water.Key;
import water.api.schemas3.ParseSetupV3;
import water.exceptions.H2OIllegalArgumentException;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.util.DistributedException;
import water.util.PojoUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static water.parser.DefaultParserProviders.GUESS_INFO;

/** A class holding parser-setup flags: kind of parser, field separator, column
 *  header labels, whether or not to allow single-quotes to quote, number of
 *  columns discovered.
 */
public class ParseSetupHandler extends Handler {

  public ParseSetupV3 guessSetup(int version, ParseSetupV3 p) {
    if (p.source_frames == null)
      throw new H2OIllegalArgumentException("No file names given for parsing.");
    Key[] fkeys = new Key[p.source_frames.length];
    for(int i=0; i < p.source_frames.length; i++) {
      fkeys[i] = p.source_frames[i].key();
      if (DKV.get(fkeys[i]) == null) throw new IllegalArgumentException("Key not loaded: "+ p.source_frames[i]);
    }

    // corrects for json putting in empty strings in the place of empty sub-arrays
    if (p.na_strings != null)
      for(int i = 0; i < p.na_strings.length; i++)
        if (p.na_strings[i] != null && p.na_strings[i].length == 0) p.na_strings[i] = null;
    ParseSetup ps;
    try{
      ps = ParseSetup.guessSetup(fkeys, new ParseSetup(p));
    } catch(Throwable ex) {
      Throwable ex2 = ex;
      if(ex instanceof DistributedException)
        ex2 = ex.getCause();
      if(ex2 instanceof ParseDataset.H2OParseException)
        throw new H2OIllegalArgumentException(ex2.getMessage());
      throw ex;
    }
    ps.setSkippedColumns(p.skipped_columns);  // setup the skipped_columns here
    ps.setParseColumnIndices(ps.getNumberColumns(), ps.getSkippedColumns());
    if(ps.errs() != null && ps.errs().length > 0) {
      p.warnings = new String[ps.errs().length];
      for (int i = 0; i < ps.errs().length; ++i)
        p.warnings[i] = ps.errs().toString();
    }
    // TODO: ParseSetup throws away the srcs list. . .
    if ((null == p.column_name_filter || "".equals(p.column_name_filter)) && (0 == p.column_offset) && (0 == p.column_count)) {
      // return the entire data preview
      PojoUtils.copyProperties(p, ps, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES, new String[]{"destination_key", "source_keys", "column_types", "parse_type"});
      p.total_filtered_column_count = p.number_columns;
    } else {
      // have to manually copy the desired parts of p.data to apply either column_name_filter or column pagination or both
      PojoUtils.copyProperties(p, ps, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES, new String[]{"destination_key", "source_keys", "column_types", "data", "parse_type"});

      String[] all_col_names = ps.getColumnNames();
      String[][] data = ps.getData();

      ArrayList<Integer> keep_indexes = new ArrayList<>();
      if (null != p.column_name_filter && ! "".equals(p.column_name_filter)) {
        // filter and then paginate columns
        Pattern pattern = Pattern.compile(p.column_name_filter);
        Matcher m =  pattern.matcher("dummy");

        for (int column = 0; column < all_col_names.length; column++) {
          m.reset(all_col_names[column]);
          if (m.matches()) keep_indexes.add(column);
        }

      } else {
        // paginate all columns
        // note: we do a little extra work below by treating this like the filter case, but the code is simpler
        for (int column = 0; column < all_col_names.length; column++) {
          keep_indexes.add(column);
        }
      }

      int width_to_return = Math.max(0, keep_indexes.size() - p.column_offset);
      if (p.column_count > 0) width_to_return = Math.min(width_to_return, p.column_count);
      String[][] filtered_data = new String[data.length][width_to_return];
      for (int row = 0; row < data.length; row++) {
        int output_column = 0;
        for (int input_column_index = p.column_offset; input_column_index < p.column_offset + width_to_return; input_column_index++) {
          // indirect through keep_indexes
          filtered_data[row][output_column++] = data[row][keep_indexes.get(input_column_index)];
        }
      }
      p.data = filtered_data;
      p.total_filtered_column_count = keep_indexes.size();
    }
    p.destination_frame = ParseSetup.createHexName(p.source_frames[0].toString());
    if( p.check_header==ParseSetup.HAS_HEADER && p.data != null && Arrays.equals(p.column_names, p.data[0])) p.data = Arrays.copyOfRange(p.data,1,p.data.length);
    // Fill in data type names for each column.
    p.column_types = ps.getColumnTypeStrings();
    p.parse_type = ps.getParseType() != null ? ps.getParseType().name() : GUESS_INFO.name();
    return p;
  }
}
