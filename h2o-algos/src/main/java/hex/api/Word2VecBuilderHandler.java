package hex.api;

import hex.schemas.Word2VecV2;
import hex.word2vec.Word2Vec;
import water.api.ModelBuilderHandler;
import water.api.Schema;


public class Word2VecBuilderHandler extends ModelBuilderHandler<Word2Vec, Word2VecV2, Word2VecV2.Word2VecParametersV2> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, Word2VecV2 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Word2VecV2 validate_parameters(int version, Word2VecV2 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}
