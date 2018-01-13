import h2o

from tests import pyunit_utils
from h2o.estimators.word2vec import H2OWord2vecEstimator


def pubdev_5112():

    print("Test retrieving a word2vec model by a key")

    words = h2o.create_frame(rows=1000,cols=1,string_fraction=1.0,missing_fraction=0.0)
    embeddings = h2o.create_frame(rows=1000,cols=100,real_fraction=1.0,missing_fraction=0.0)
    word_embeddings = words.cbind(embeddings)

    w2v_model = H2OWord2vecEstimator.from_external(external=word_embeddings)

    model_id = w2v_model.model_id
    model = h2o.get_model(model_id)

    assert model, "Worder2Vec model without a training frame was retrived"

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5112)
else:
    pubdev_5112()
