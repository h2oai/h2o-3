from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.word2vec import H2OWord2vecEstimator


def word2vec_to_frame():
    print("Test converting a word2vec model to a Frame")

    words = h2o.create_frame(rows=1000,cols=1,string_fraction=1.0,missing_fraction=0.0)
    embeddings = h2o.create_frame(rows=1000,cols=100,real_fraction=1.0,missing_fraction=0.0)
    word_embeddings = words.cbind(embeddings)

    w2v_model = H2OWord2vecEstimator(pre_trained=word_embeddings)
    w2v_model.train(training_frame=word_embeddings)

    w2v_frame = w2v_model.to_frame()

    word_embeddings.names = w2v_frame.names
    assert word_embeddings.as_data_frame().equals(word_embeddings.as_data_frame()), "Source and generated embeddings match"

if __name__ == "__main__":
    pyunit_utils.standalone_test(word2vec_to_frame)
else:
    word2vec_to_frame()
