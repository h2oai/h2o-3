package hex.schemas;

import hex.word2vec.Word2Vec;
import hex.word2vec.Word2VecModel.Word2VecParameters;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;

public class Word2VecV3 extends ModelBuilderSchema<Word2Vec,Word2VecV3,Word2VecV3.Word2VecParametersV3> {
  public static final class Word2VecParametersV3 extends ModelParametersSchemaV3<Word2VecParameters, Word2VecParametersV3> {
    public static String[] fields = new String[] {
            "model_id",
            "training_frame",
            "min_word_freq",
            "word_model",
            "norm_model",
            "vec_size",
            "window_size",
            "sent_sample_rate",
            "init_learning_rate",
            "epochs",
            "pre_trained",
            "max_runtime_secs",
            "export_checkpoints_dir"
    };

    /**
     *
     */
    @API(help="Set size of word vectors")
    public int vec_size;

    /**
     *
     */
    @API(help="Set max skip length between words")
    public int window_size;

    /**
     *
     */
    @API(help="Set threshold for occurrence of words. Those that appear with higher frequency in the training data\n" +
            "\t\twill be randomly down-sampled; useful range is (0, 1e-5)")
    public float sent_sample_rate;

    /**
     *
     */
    @API(help="Use Hierarchical Softmax", values = {"HSM"})
    public Word2Vec.NormModel norm_model;

    /**
     *
     */
    @API(help="Number of training iterations to run")
    public int epochs;

    /**
     *
     */
    @API(help="This will discard words that appear less than <int> times")
    public int min_word_freq;

    /**
     *
     */
    @API(help="Set the starting learning rate")
    public float init_learning_rate;

    /**
     *
     */
    @API(help="Use the Skip-Gram model", values = {"SkipGram"})
    public Word2Vec.WordModel word_model;

    /**
     *
     */
    @API(help="Id of a data frame that contains a pre-trained (external) word2vec model")
    public KeyV3.FrameKeyV3 pre_trained;
  }
}
