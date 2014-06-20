package hex.schemas;

import hex.Summary;
import water.*;
import water.api.API;
import water.api.Schema;
import water.fvec.Frame;
import water.fvec.Vec;

public class SummaryV2 extends Schema<SummaryHandler,SummaryV2> {

  // Input fields
  @API(help="Input source frame",required=true)
  public Key src;

  @API(help="Max binning for quantiles")
  public int max_qbins;
  

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
    final double[] mins;

    @API(help="max")
    final double[] maxs;

    @API(help="mean")
    final double mean;

    @API(help="sigma")
    final double sigma;

    @API(help="NAs")
    final long nas;

    @API(help="Zeros")
    final long zeros;

    @API(help="datatype: {time, enum, int, real}")
    final String type;

    @API(help="domain; not-null for enum columns only")
    final String[] domain;

    @API(help="Histogram Counts")
    final long[] hcnt;

    @API(help="Histogram Bin Starts")
    final double[] hstarts;

    @API(help="Percentiles")
    final double[] pct;

    @API(help="Percentile Values")
    final double[] pctile;

    @API(help="Cardinality")
    final int cardinality;

    Col( String name, Vec vec, Summary sum ) {
      label=name;
      missing = vec.naCnt();
      mins = sum._mins;
      maxs = sum._maxs;
      mean = vec.mean();
      sigma = vec.sigma();
      type = vec.isEnum() ? "enum" : vec.isUUID() ? "uuid" : (vec.isInt() ? (vec.isTime() ? "time" : "int") : "real");
      domain = vec.domain();
      nas   = sum._stat0._nas;
      zeros = sum._stat0._zeros;
      hcnt = sum._hcnt;
      hstarts = new double[hcnt.length];
      for( int i=0; i<hcnt.length; i++ ) hstarts[i] = sum.binValue(i);
      pct = sum._stats._pct;
      pctile  = sum._stats._pctile;
      cardinality = sum._stats._cardinality;
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
    if( max_qbins < 0 || max_qbins > 1e8 ) throw new IllegalArgumentException("max_qbins out of range");
    if( max_qbins == 0 ) max_qbins = 1000;
    h._max_qbins = max_qbins;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override public SummaryV2 fillFrom( SummaryHandler h ) {
    columns = new Col[h._fr.numCols()];
    Vec[] vecs = h._fr.vecs();
    for( int i=0; i<columns.length; i++ )
      columns[i] = new Col(h._fr._names[i],vecs[i],h._summary[i]);
    return this;
  }

  // Return a URL to invoke Summary on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/Summary?src="+fr._key; }
}
