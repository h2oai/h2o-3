package hex.api;

import hex.schemas.Word2VecSynonymsV3;
import hex.word2vec.Word2VecModel;
import water.DKV;
import water.api.Handler;

import java.util.*;

public class Word2VecHandler extends Handler {

  public Word2VecSynonymsV3 findSynonyms(int version, Word2VecSynonymsV3 args) {
    Word2VecModel model = DKV.getGet(args.model.key());
    if (model == null)
      throw new IllegalArgumentException("missing source model " + args.model);

    Map<String, Float> synonyms = model.findSynonyms(args.target, args.count);

    List<Map.Entry<String, Float>> result = new ArrayList<>(synonyms.entrySet());
    Collections.sort(result, new Comparator<Map.Entry<String, Float>>() {
      @Override
      public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
        return o2.getValue().compareTo(o1.getValue()); // reverse sort
      }
    });
    args.words = new String[result.size()];
    args.scores = new double[result.size()];
    int i = 0;
    for (Map.Entry<String, Float> entry : result) {
      args.words[i] = entry.getKey();
      args.scores[i] = entry.getValue();
      i++;
    }
    return args;
  }

}
