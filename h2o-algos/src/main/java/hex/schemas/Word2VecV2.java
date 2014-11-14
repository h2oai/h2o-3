package hex.schemas;

import hex.word2vec.Word2Vec;
import hex.word2vec.Word2VecModel.Word2VecParameters;
import water.api.API;
import water.api.ModelParametersSchema;
import water.fvec.Frame;

public class Word2VecV2 extends ModelBuilderSchema<Word2Vec,Word2VecV2,Word2VecV2.Word2VecParametersV2> {

  public static final class Word2VecParametersV2 extends ModelParametersSchema<Word2VecParameters, Word2VecParametersV2> {
    static public String[] own_fields = new String[] {
            "training_frame",
            "ignored_columns",
            "minWordFreq",
            "wordModel",
            "normModel",
            "negSampleCnt",
            "vecSize",
            "windowSize",
            "sentSampleRate",
            "learningRate",
            "epochs"
    };

    /**
     *
     */
    @API(help="Set size of word vectors; default is 100", required = true)
    public int vecSize = 100;

    /**
     *
     */
    @API(help="Set max skip length between words; default is 5", required = true)
    public int windowSize = 5;

    /**
     *
     */
    @API(help="Set threshold for occurrence of words. Those that appear with higher frequency in the training data\n" +
            "\t\twill be randomly down-sampled; default is 1e-3, useful range is (0, 1e-5)", required = true)
    public float sentSampleRate = 1e-3f;

    /**
     *
     */
    @API(help="Use Hierarchical Softmax or Negative Sampling", values = {"HSM", "NegSampling"}, required = true)
    public Word2Vec.NormModel normModel;

    /**
     *
     */
    @API(help="Number of negative examples; default is 5, common values are 3 - 10 (0 = not used)")
    public int negSampleCnt = 5;

    /**
     *
     */
    @API(help="Number of training iterations to run (default 5)",  required = true)
    public int epochs = 5;
    /**
     *
     */
    @API(help="This will discard words that appear less than <int> times; default is 5", required = true)
    public int minWordFreq = 5;
    /**
     *
     */
    @API(help="Set the starting learning rate; default is 0.05", required = true)
    public double learningRate = 0.05;
    /**
     *
     */
    @API(help="Use the continuous bag of words model or the Skip-Gram model", values = {"CBOW", "SkipGram"}, required = true)
    public Word2Vec.WordModel wordModel;

  }

  // Return a URL to invoke Word2Vec on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/Word2Vec?training_frame="+fr._key; }
  
}
