package hex.schemas;

import hex.word2vec.Word2Vec;
import hex.word2vec.Word2VecModel.Word2VecParameters;
import water.api.API;
import water.api.ModelParametersSchema;

public class Word2VecV3 extends ModelBuilderSchema<Word2Vec,Word2VecV3,Word2VecV3.Word2VecParametersV3> {
  public static final class Word2VecParametersV3 extends ModelParametersSchema<Word2VecParameters, Word2VecParametersV3> {
    static public String[] fields = new String[] {
            "minWordFreq",
            "wordModel",
            "normModel",
            "negSampleCnt",
            "vecSize",
            "windowSize",
            "sentSampleRate",
            "initLearningRate",
            "epochs"
    };

    /**
     *
     */
    @API(help="Set size of word vectors", required = true)
    public int vecSize;

    /**
     *
     */
    @API(help="Set max skip length between words", required = true)
    public int windowSize;

    /**
     *
     */
    @API(help="Set threshold for occurrence of words. Those that appear with higher frequency in the training data\n" +
            "\t\twill be randomly down-sampled; useful range is (0, 1e-5)", required = true)
    public float sentSampleRate;

    /**
     *
     */
    @API(help="Use Hierarchical Softmax or Negative Sampling", values = {"HSM", "NegSampling"}, required = true)
    public Word2Vec.NormModel normModel;

    /**
     *
     */
    @API(help="Number of negative examples, common values are 3 - 10 (0 = not used)", required = true)
    public int negSampleCnt;

    /**
     *
     */
    @API(help="Number of training iterations to run",  required = true)
    public int epochs;
    /**
     *
     */
    @API(help="This will discard words that appear less than <int> times", required = true)
    public int minWordFreq;
    /**
     *
     */
    @API(help="Set the starting learning rate", required = true)
    public float initLearningRate;
    /**
     *
     */
    @API(help="Use the continuous bag of words model or the Skip-Gram model", values = {"CBOW", "SkipGram"}, required = true)
    public Word2Vec.WordModel wordModel;

  }
}
