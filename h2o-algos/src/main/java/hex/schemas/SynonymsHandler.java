package hex.schemas;

import hex.word2vec.Word2VecModel;
import water.DKV;
import water.Iced;
import water.Key;
import water.api.Handler;
import hex.schemas.SynonymsHandler.Synonyms;

import java.util.HashMap;

public class SynonymsHandler extends Handler<Synonyms, SynonymV1> {

  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  /**
   *  Synonym: A process in which information is successively passed on.
   */
  public static final class Synonyms extends Iced {
    // Inputs
    Key key;
    String target;
    int cnt;

    //Outputs
    String[] synonyms;
    float[]  cos_sim;
  }

  public SynonymV1 findSynonyms(int version, Synonyms synonym) {
    Word2VecModel w2vmodel = DKV.get(synonym.key).get();
    HashMap<String, Float> hm = w2vmodel.findSynonyms(synonym.target, synonym.cnt);
    synonym.synonyms = hm.keySet().toArray(new String[hm.keySet().size()]);
    Float[] f = hm.values().toArray(new Float[hm.keySet().size()]);
    float[] cos_sim = new float[f.length];
    for(int i = 0; i < f.length; ++i) cos_sim[i] = f[i];
    synonym.cos_sim = cos_sim;
    return schema(version).fillFromImpl(synonym);
  }

  @Override protected SynonymV1 schema(int version) { return new SynonymV1(); }
}