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
    
        cnt = 10
        synonyms = w2v_model.find_synonyms("horse", cnt)
        print(synonyms)
        assert len(synonyms) == cnt, "There should be ten synonyms."
        
        # GH-16192 find_synonyms returns empty dataset if there is no synonyms to find
        synonyms = w2v_model.find_synonyms("hhorse", cnt)
        print(synonyms)
        assert len(synonyms) == 0, "There should be zero synonyms."
        

if __name__ == "__main__":
    pyunit_utils.standalone_test(word2vec)
else:
    word2vec()
