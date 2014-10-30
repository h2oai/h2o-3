package hex.schemas;

import hex.word2vec.Word2VecModel;
import water.api.*;
import water.util.PojoUtils;


public class Word2VecModelV2 extends ModelSchema<Word2VecModel, Word2VecModel.Word2VecParameters, Word2VecModel.Word2VecOutput, Word2VecModelV2 > {

  public static final class Word2VecModelOutputV2 extends ModelOutputSchema<Word2VecModel.Word2VecOutput, Word2VecModelOutputV2> {

    @API(help="Vocabulary size")
    public int vocabSize;

    @API(help="Word vector size")
    public int vecSize;

    @API(help="Words[vocabSize]")
    public String[/*vocabSize*/] words;

    @API(help="Word vectors")
    public int vecs[/*vocabSize*/][/*vecSize*/];


    @Override public Word2VecModel.Word2VecOutput createImpl() {
      Word2VecModel.Word2VecOutput impl = new Word2VecModel.Word2VecOutput();
      PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }

    @Override public Word2VecModelOutputV2 fillFromImpl( Word2VecModel.Word2VecOutput impl) {
      PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
      return this;
    }
  } // Word2VecModelOutputV2


  //==========================
  // Custom adapters go here
  public Word2VecV2.Word2VecParametersV2 createParametersSchema() { return new Word2VecV2.Word2VecParametersV2(); }
  public Word2VecModelOutputV2 createOutputSchema() { return new Word2VecModelOutputV2(); }

  // Version&Schema-specific filling into the impl
  @Override public Word2VecModel createImpl() {
    Word2VecV2.Word2VecParametersV2 p = ((Word2VecV2.Word2VecParametersV2)this.parameters);
    Word2VecModel.Word2VecParameters parms = p.createImpl();
    return new Word2VecModel( key, p.training_frame, parms);
  }

  // Version&Schema-specific filling from the impl
  @Override public Word2VecModelV2 fillFromImpl( Word2VecModel m ) { return super.fillFromImpl(m); }
}
