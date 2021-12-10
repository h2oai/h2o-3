package hex.word2vec;

import hex.ModelMetrics;
import hex.ModelMojoWriter;
import water.MemoryManager;
import water.parser.BufferedString;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * MOJO serializer for word2vec model.
 */
public class Word2VecMojoWriter extends ModelMojoWriter<Word2VecModel, Word2VecModel.Word2VecParameters, Word2VecModel.Word2VecOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public Word2VecMojoWriter() {}

  public Word2VecMojoWriter(Word2VecModel model) {
    super(model);
  }

  @Override public String mojoVersion() {
    return "1.00";
  }

  @Override
  protected void writeModelData() throws IOException {
    writekv("vec_size", model._parms._vec_size);
    writekv("vocab_size", model._output._words.length);

    // Vocabulary
    startWritingTextFile("vocabulary");
    for (BufferedString word : model._output._words) {
      writeln(word.toString(), true);
    }
    finishWritingTextFile();

    // Vectors
    ByteBuffer bb = ByteBuffer.wrap(MemoryManager.malloc1(model._output._vecs.length * 4));
    for (float v : model._output._vecs)
      bb.putFloat(v);
    writeblob("vectors", bb.array());
  }
  
  @Override
  public ModelMetrics.MetricBuilderFactory getModelBuilderFactory() { return null; }
}
