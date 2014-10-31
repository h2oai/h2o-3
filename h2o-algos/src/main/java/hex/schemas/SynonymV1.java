package hex.schemas;

import water.api.API;
import water.api.Schema;
import hex.schemas.SynonymsHandler.Synonyms;
import water.Key;

public class SynonymV1 extends Schema<Synonyms, SynonymV1> {

  // Input fields
  @API(help="A word2vec model key.") public String key;
  @API(help="The target string to find synonyms.") public String target;
  @API(help="Find the top `cnt` synonyms of the target word.") public int cnt;


  //Outputs
//    Key      _key;
  @API(help="The synonyms.", direction=API.Direction.OUTPUT) public String[] synonyms;
  @API(help="The cosine similarities.", direction=API.Direction.OUTPUT) public float[]  cos_sim;

  @Override public SynonymsHandler.Synonyms createImpl() {
    Synonyms c = new Synonyms();
    c._w2vec_key = Key.make(key);
    c._cnt = cnt;
    c._target = target;
    return c;
  }

  @Override public SynonymV1 fillFromImpl(Synonyms synonym) {
    key = synonym._w2vec_key.toString();
    target = synonym._target;
    cnt = synonym._cnt;
    synonyms = synonym._synonyms;
    cos_sim = synonym._cos_sim;
    return this;
  }
}
