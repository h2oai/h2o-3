package hex.schemas;

import hex.word2vec.Word2VecModel;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;


public class Word2VecModelV3 extends ModelSchema<Word2VecModel, Word2VecModelV3, Word2VecModel.Word2VecParameters, Word2VecV3.Word2VecParametersV3, Word2VecModel.Word2VecOutput, Word2VecModelV3.Word2VecModelOutputV3> {

  public static final class Word2VecModelOutputV3 extends ModelOutputSchema<Word2VecModel.Word2VecOutput, Word2VecModelOutputV3> {
  } // Word2VecModelOutputV2


  //==========================
  // Custom adapters go here
  public Word2VecV3.Word2VecParametersV3 createParametersSchema() { return new Word2VecV3.Word2VecParametersV3(); }
  public Word2VecModelOutputV3 createOutputSchema() { return new Word2VecModelOutputV3(); }

  // Version&Schema-specific filling into the impl
  @Override public Word2VecModel createImpl() {
    Word2VecModel.Word2VecParameters parms = parameters.createImpl();
    return new Word2VecModel( model_id.key(), parms, null);
  }
}
