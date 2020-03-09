from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.word2vec import H2OWord2vecEstimator


def word2vec():
    for word_model in ["SkipGram", "CBOW"]:
        print("word2vec %s smoke test on text8 dataset" % word_model)
    
        train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/text8.gz"), header=1, col_types=["string"])
    
        w2v_model = H2OWord2vecEstimator(epochs=1, word_model=word_model)
        w2v_model.train(training_frame=train)
    
        synonyms = w2v_model.find_synonyms("horse", 3)
        print(synonyms)
    
        assert len(synonyms) == 3, "there should be three synonmys"


if __name__ == "__main__":
    pyunit_utils.standalone_test(word2vec)
else:
    word2vec()
