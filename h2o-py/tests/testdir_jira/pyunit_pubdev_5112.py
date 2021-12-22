import h2o
from h2o.exceptions import H2OValueError

from tests import pyunit_utils
from h2o.estimators.word2vec import H2OWord2vecEstimator


def pubdev_5112():
    words = h2o.create_frame(rows=10, cols=1, string_fraction=1.0, missing_fraction=0.0)
    embeddings = h2o.create_frame(rows=10, cols=100, real_fraction=1.0, missing_fraction=0.0)
    word_embeddings = words.cbind(embeddings)

    w2v_model = H2OWord2vecEstimator.from_external(external=word_embeddings)

    model_id = w2v_model.model_id
    model = h2o.get_model(model_id)

    assert model, "Worder2Vec model without a training frame was retrieved"

    # Only leading column should be of type String
    leading_column_string_error = False
    try:
        string_frame = h2o.create_frame(rows=10, cols=10, real_fraction=1.0, missing_fraction=0.0)
        H2OWord2vecEstimator.from_external(external=string_frame)
    except H2OValueError:
        leading_column_string_error = True

    assert leading_column_string_error, "Word2Vec pre-trained model should be checked for the leading column" \
                                        " to be string"
    # Other columns should be non-string type
    multiple_string_columns_error = False
    try:
        string_frame = h2o.create_frame(rows=10, cols=10, string_fraction=1.0, missing_fraction=0.0)
        H2OWord2vecEstimator.from_external(external=string_frame)
    except H2OValueError:
        multiple_string_columns_error = True

    assert multiple_string_columns_error, "Word2Vec pre-trained model should be checked for columns not to have a" \
                                          " String type except for the leading column"

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5112)
else:
    pubdev_5112()
