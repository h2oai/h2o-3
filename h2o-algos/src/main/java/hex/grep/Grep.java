package hex.grep;

import hex.ModelBuilder;
import hex.ModelCategory;
import water.*;
import water.fvec.ByteVec;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.Log;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 *  Grep model builder... building a trivial GrepModel
 */
public class Grep extends ModelBuilder<GrepModel,GrepModel.GrepParameters,GrepModel.GrepOutput> {
  public Grep( GrepModel.GrepParameters parms ) { super(parms); init(false); }
  @Override protected GrepDriver trainModelImpl() { return new GrepDriver(); }
  @Override public ModelCategory[] can_build() { return new ModelCategory[]{ModelCategory.Unknown}; }
  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Experimental; }
  Pattern _pattern = null;
  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   *
   *  Validate the regex. */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    if( _parms._regex == null ) {
      error("_regex", "regex is missing");
    } else {
      try { _pattern = Pattern.compile(_parms._regex); }
      catch( PatternSyntaxException pse ) { error("regex", pse.getMessage()); }
    }
    if( _parms._train == null ) return;
    Vec[] vecs = _parms.train().vecs();
    if( vecs.length != 1 )
      error("_train","Frame must contain exactly 1 Vec (of raw text)");
    if( !(vecs[0] instanceof ByteVec) )
      error("_train","Frame must contain exactly 1 Vec (of raw text)");
  }

  // ----------------------
  private class GrepDriver extends Driver {

    @Override public void computeImpl() {
      GrepModel model = null;
      try {
        init(true);

        // The model to be built
        model = new GrepModel(dest(), _parms, new GrepModel.GrepOutput(Grep.this));
        model.delete_and_lock(_job);

        // ---
        // Run the main Grep Loop
        GrepGrep gg = new GrepGrep(_pattern).doAll(train().vecs()[0]);

        // Fill in the model
        model._output._matches = Arrays.copyOf(gg._matches,gg._cnt);
        model._output._offsets = Arrays.copyOf(gg._offsets,gg._cnt);

        StringBuilder sb = new StringBuilder("Grep: \n");
        sb.append(Arrays.toString(model._output._matches)).append("\n");
        sb.append(Arrays.toString(model._output._offsets)).append("\n");
        Log.info(sb);
      } finally {
        if( model != null ) model.unlock(_job);
      }
    }
  }

  private class ByteSeq implements CharSequence {
    private final byte _bs0[];
    private final byte _bs1[];
    ByteSeq( Chunk chk0, Chunk chk1 ) { _bs0 = chk0.getBytes(); _bs1 = chk1==null ? null : chk1.getBytes(); }

    @Override public char charAt(int idx ) {
      return (char)(idx < _bs0.length ? _bs0[idx] : _bs1[idx-_bs0.length]);
    }
    @Override public int length( ) { return _bs0.length+(_bs1==null?0:_bs1.length); }
    @Override public ByteSeq subSequence( int start, int end ) { throw H2O.unimpl(); }
    @Override public String toString() { throw H2O.unimpl(); }

    String str( int s, int e ) { return new String(_bs0,s,e-s); }
  }


  private class GrepGrep extends MRTask<GrepGrep> {
    private final Pattern _pattern;
    // Outputs, hopefully not too big for once machine!
    String[] _matches;
    long  [] _offsets;
    int _cnt;

    GrepGrep( Pattern pattern ) { _pattern = pattern; }
    @Override public void map( Chunk chk ) {
      _matches = new String[1]; // Result holders; will lazy expand
      _offsets = new long  [1];
      ByteSeq bs = new ByteSeq(chk,chk.nextChunk());
      // We already checked that this is an instance of a ByteVec, which means
      // all the Chunks contain raw text as byte arrays.
      Matcher m = _pattern.matcher(bs);
      while( m.find() && m.start() < bs._bs0.length )
        add(bs.str(m.start(),m.end()),chk.start()+m.start());
      _job.update(chk._len);         // Whole chunk of work, done all at once
    }
    @Override public void reduce( GrepGrep gg1 ) {
      GrepGrep gg0 = this;
      if( gg0._cnt < gg1._cnt ) { gg0 = gg1; gg1 = this; } // Larger result on left
      for( int i=0; i<gg1._cnt; i++ )
        gg0.add(gg1._matches[i], gg1._offsets[i]);
      if( gg0 != this ) {
        _matches = gg0._matches;
        _offsets = gg0._offsets;
        _cnt = gg0._cnt;
      }
    }

    private void add( String s, long off ) {
      if( _cnt == _matches.length ) {
        _matches = Arrays.copyOf(_matches,_cnt<<1);
        _offsets = Arrays.copyOf(_offsets,_cnt<<1);
      }
      _matches[_cnt  ] = s;
      _offsets[_cnt++] = off;
    }
  }
}
