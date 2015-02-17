package hex.schemas;

import hex.word2vec.Word2VecModel;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;


public class Word2VecModelV2 extends ModelSchema<Word2VecModel, Word2VecModelV2, Word2VecModel.Word2VecParameters, Word2VecV2.Word2VecParametersV2, Word2VecModel.Word2VecOutput, Word2VecModelV2.Word2VecModelOutputV2> {

  public static final class Word2VecModelOutputV2 extends ModelOutputSchema<Word2VecModel.Word2VecOutput, Word2VecModelOutputV2> {
  } // Word2VecModelOutputV2


  //==========================
  // Custom adapters go here
  public Word2VecV2.Word2VecParametersV2 createParametersSchema() { return new Word2VecV2.Word2VecParametersV2(); }
  public Word2VecModelOutputV2 createOutputSchema() { return new Word2VecModelOutputV2(); }

  // Version&Schema-specific filling into the impl
  @Override public Word2VecModel createImpl() {
    Word2VecV2.Word2VecParametersV2 p = ((Word2VecV2.Word2VecParametersV2)this.parameters);
    Word2VecModel.Word2VecParameters parms = p.createImpl();
    return new Word2VecModel( key.key(), parms, null);
  }
}
