package hex.schemas;

import water.*;
import water.api.API;
import water.api.Schema;
import water.fvec.Frame;
import water.fvec.Vec;

public class SummaryV2 extends Schema<SummaryHandler,SummaryV2> {

  // Input fields
  @API(help="Input source frame",required=true)
  public Key src;

  // Output fields
  @API(help="Columns")
  Col[] columns;

  // Output fields one-per-column
  private static class Col extends Iced {
    @API(help="label")
    final String label;

    @API(help="missing")
    final long missing;

    @API(help="min")
    final double min;

    @API(help="max")
    final double max;

    @API(help="mean")
    final double mean;

    @API(help="sigma")
    final double sigma;

    @API(help="datatype: {time, enum, int, real}")
    final String type;

    @API(help="domain; not-null for enum columns only")
    final String[] domain;

    Col( String name, Vec vec ) {
      label=name;
      missing = vec.naCnt();
      min = vec.min();
      max = vec.max();
      mean = vec.mean();
      sigma = vec.sigma();
      type = vec.isEnum() ? "enum" : vec.isUUID() ? "uuid" : (vec.isInt() ? (vec.isTime() ? "time" : "int") : "real");
      domain = vec.domain();
    }
  }

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public SummaryV2 fillInto( SummaryHandler h ) {
    Value val = DKV.get(src);
    if( val == null ) throw new IllegalArgumentException("Missing key "+src);
    if( !val.isFrame() ) throw new IllegalArgumentException("Not a Frame "+src);
    h._fr = val.get();
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override public SummaryV2 fillFrom( SummaryHandler h ) {
    columns = new Col[h._fr.numCols()];
    Vec[] vecs = h._fr.vecs();
    for( int i=0; i<columns.length; i++ )
      columns[i] = new Col(h._fr._names[i],vecs[i]);
    return this;
  }

  // Return a URL to invoke Summary on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/Summary?src="+fr._key; }
}
