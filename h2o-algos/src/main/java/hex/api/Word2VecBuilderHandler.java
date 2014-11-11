package hex.api;

import hex.word2vec.Word2Vec;
import hex.schemas.Word2VecV2;
import water.H2O;
import water.api.ModelBuilderHandler;


public class Word2VecBuilderHandler extends ModelBuilderHandler<Word2Vec, Word2VecV2, Word2VecV2.Word2VecParametersV2> {
  @Override protected Word2VecV2 schema(int version) {
    switch (version) {
      case 2:   { Word2VecV2 b = new Word2VecV2(); b.parameters = b.createParametersSchema(); return b; }
      default:  throw H2O.fail("Bad version for ModelBuilder schema: " + version);
    }
  }
}
