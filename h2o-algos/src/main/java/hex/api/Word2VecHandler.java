package hex.api;

import hex.schemas.Word2VecSynonymsV3;
import hex.schemas.Word2VecTransformV3;
import hex.word2vec.Word2VecModel;
import water.DKV;
import water.api.Handler;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;
import water.util.Log;

import java.util.*;

public class Word2VecHandler extends Handler {

  public Word2VecSynonymsV3 findSynonyms(int version, Word2VecSynonymsV3 args) {
    Word2VecModel model = DKV.getGet(args.model.key());
    if (model == null)
      throw new IllegalArgumentException("missing source model " + args.model);

    Map<String, Float> synonyms = model.findSynonyms(args.word, args.count);

    List<Map.Entry<String, Float>> result = new ArrayList<>(synonyms.entrySet());
    Collections.sort(result, new Comparator<Map.Entry<String, Float>>() {
      @Override
      public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
        return o2.getValue().compareTo(o1.getValue()); // reverse sort
      }
    });
    args.synonyms = new String[result.size()];
    args.scores = new double[result.size()];
    if(result.size() > 0) {
      int i = 0;
      for (Map.Entry<String, Float> entry : result) {
        args.synonyms[i] = entry.getKey();
        args.scores[i] = entry.getValue();
        i++;
      }
    }
    if (result.size() < args.count) {
      Log.warn(String.format("The result number of synonyms (%d) is less than the 'count' parameter (%d).", args.synonyms.length, args.count));
    }
    return args;
  }

  public Word2VecTransformV3 transform(int version, Word2VecTransformV3 args) {
    Word2VecModel model = DKV.getGet(args.model.key());
    if (model == null)
      throw new IllegalArgumentException("missing source model " + args.model);

    Frame words = DKV.getGet(args.words_frame.key());
    if (words == null)
      throw new IllegalArgumentException("missing words frame " + args.words_frame);

    if (words.numCols() != 1) {
      throw new IllegalArgumentException("words frame is expected to have a single string column, got" + words.numCols());
    }

    if (args.aggregate_method == null)
      args.aggregate_method = Word2VecModel.AggregateMethod.NONE;

    Frame vectors = model.transform(words.vec(0), args.aggregate_method);
    args.vectors_frame = new KeyV3.FrameKeyV3(vectors._key);
    return args;
  }

}
