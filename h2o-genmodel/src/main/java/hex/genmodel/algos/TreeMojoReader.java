package hex.genmodel.algos;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;

/**
 */
public abstract class TreeMojoReader<M extends TreeBasedModel> extends ModelMojoReader<M> {

  @Override
  protected void readModelData() throws IOException {
    _model._ntrees = readkv("n_trees");
    _model._compressed_trees = new byte[_model._ntrees * _model._nclasses][];

    for (int i = 0; i < _model._nclasses; i++)
      for (int j = 0; j < _model._ntrees; j++)
        _model._compressed_trees[i * _model._ntrees + j] = readblob(String.format("trees/t%02d_%03d.bin", j, i));
  }

}
