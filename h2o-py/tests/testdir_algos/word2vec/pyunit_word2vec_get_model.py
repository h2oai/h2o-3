from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.word2vec import H2OWord2vecEstimator


def word2vec_get_model():
    print("Test retrieving a word2vec model by a key")

    words = h2o.create_frame(rows=1000,cols=1,string_fraction=1.0,missing_fraction=0.0)
    embeddings = h2o.create_frame(rows=1000,cols=100,real_fraction=1.0,missing_fraction=0.0)
    word_embeddings = words.cbind(embeddings)

    w2v_model = H2OWord2vecEstimator(pre_trained=word_embeddings)
    w2v_model.train(training_frame=word_embeddings)

    model_id = w2v_model.model_id
    model = h2o.get_model(model_id)

    assert model, "Model was retrived"

if __name__ == "__main__":
    pyunit_utils.standalone_test(word2vec_get_model)
else:
    word2vec_get_model()
