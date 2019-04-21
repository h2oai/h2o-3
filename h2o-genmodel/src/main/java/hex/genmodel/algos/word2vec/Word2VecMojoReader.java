package hex.genmodel.algos.word2vec;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;

public class Word2VecMojoReader extends ModelMojoReader<Word2VecMojoModel> {

  @Override
  public String getModelName() {
    return "Word2Vec";
  }

  @Override
  protected void readModelData() throws IOException {
    final int vocabSize = readkv("vocab_size", -1);
    final int vecSize = readkv("vec_size", -1);

    _model._vecSize = vecSize;
    _model._embeddings = new HashMap<>(vocabSize);

    byte[] rawVectors = readblob("vectors");
    if (rawVectors.length != vocabSize * vecSize * 4)
      throw new IOException("Corrupted vector representation, unexpected size: " + rawVectors.length);
    ByteBuffer bb = ByteBuffer.wrap(rawVectors);

    Iterator<String> vocabulary = readtext("vocabulary", true).iterator();
    while (vocabulary.hasNext()) {
      float[] vec = new float[vecSize];
      for (int i = 0; i < vecSize; i++)
        vec[i] = bb.getFloat();
      _model._embeddings.put(vocabulary.next(), vec);
    }

    if (_model._embeddings.size() != vocabSize)
      throw new IOException("Corrupted model, unexpected number of words: " + _model._embeddings.size());
  }

  @Override
  protected Word2VecMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new Word2VecMojoModel(columns, domains, responseColumn);
  }

  @Override public String mojoVersion() {
    return "1.00";
  }

}
