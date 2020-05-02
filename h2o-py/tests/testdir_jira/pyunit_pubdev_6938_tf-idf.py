from h2o import H2OFrame
from h2o.information_retrieval.tf_idf import tf_idf
from tests import pyunit_utils


def tf_idf_small_data():
    input_fr, expected_fr = get_simple_test_frames()
    out_frame = tf_idf(input_fr)

    assert out_frame == expected_fr


def get_simple_test_frames():
    doc_ids = [0, 1, 2]
    documents = [
        'A B C',
        'A A A Z',
        'C C B C'
    ]

    input_frame = H2OFrame({'DocID': doc_ids, 'Document': documents},
                           column_types=['numeric', 'string'])

    out_doc_ids = [
        0, 1, 0, 2, 0, 2, 1
    ]
    out_tokens = [
        "A", "A", "B", "B", "C", "C", "Z"
    ]
    out_TFs = [
        1, 3, 1, 1, 1, 3, 1
    ]
    out_IDFs =[
        0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.69314
    ]
    out_TFIDFs =[
        0.28768, 0.86304, 0.28768, 0.28768, 0.28768, 0.86304, 0.69314
    ]

    expected_out_frame = H2OFrame({'DocID': out_doc_ids, 'Token': out_tokens,
                                   'TF': out_TFs, 'IDF': out_IDFs, 'TF_IDF': out_TFIDFs},
                                  column_types=['numeric', 'string', 'numeric', 'numeric', 'numeric'])

    return input_frame, expected_out_frame


if __name__ == "__main__":
    pyunit_utils.standalone_test(tf_idf_small_data)
else:
    tf_idf_small_data()
