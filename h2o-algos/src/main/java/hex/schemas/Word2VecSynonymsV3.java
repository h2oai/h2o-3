package hex.schemas;

import water.Iced;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

public class Word2VecSynonymsV3 extends SchemaV3<Iced, Word2VecSynonymsV3> {
  @API(help="Source word2vec Model", required = true, direction = API.Direction.INPUT)
  public KeyV3.ModelKeyV3 model;
  @API(help="Target word", required = true, direction = API.Direction.INPUT)
  public String target;
  @API(help="Number of synonyms", required = true, direction = API.Direction.INPUT)
  public int count;
  @API(help="Synonymous word")
  public String[] words;
  @API(help="Similarity scores")
  public double[] scores;
}
