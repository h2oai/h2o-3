from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.word2vec import H2OWord2vecEstimator


def word2vec():
    print("word2vec smoke test on text8 dataset")

    train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/text8.gz"), header=1, col_types=["string"])

    w2v_model = H2OWord2vecEstimator(epochs=1)
    w2v_model.train(training_frame=train)

    synonyms = w2v_model.find_synonyms("horse", 3)
    print(synonyms)

    assert bool(synonyms), "synonyms should not be empty"

if __name__ == "__main__":
    pyunit_utils.standalone_test(word2vec)
else:
    word2vec()
