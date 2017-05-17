package hex.genmodel.algos.word2vec;

/**
 * Interface for models implementing Word Embeddings
 */
public interface WordEmbeddingModel {

  /**
   * Dimensionality of the vector space of this Word Embedding model
   * @return length of word embeddings
   */
  int getVecSize();

  /**
   * Transforms a given a word into a word vector
   * @param word input word
   * @param output pre-allocated word vector embedding
   * @return word vector embedding or null if the word is an out-of-dictionary word
   */
  float[] transform0(String word, float[] output);

}
