from tests import pyunit_utils
from h2o import H2OFrame
from h2o.information_retrieval.tf_idf import tf_idf


def tf_idf_small_data(preprocess):
    input_fr, expected_fr = get_simple_test_frames(preprocess)
    out_frame = tf_idf(input_fr)

    assert out_frame == expected_fr


def get_simple_input_test_frame():
    doc_ids = [0, 1, 2]
    documents = [
        'A B C',
        'A A A Z',
        'C C B C'
    ]
    
    return H2OFrame({'DocID': doc_ids, 'Document': documents},
                    column_types=['numeric', 'string'])


def get_simple_preprocessed_input_test_frame():
    doc_ids = [
        0, 0, 0, 
        1, 1, 1, 1,
        2, 2, 2, 2
    ]
    words = [
        'A', 'B', 'C',
        'A', 'A', 'A', 'Z',
        'C', 'C', 'B', 'C'
    ]
    
    return H2OFrame({'DocID': doc_ids, 'Words': words},
                    column_types=['numeric', 'string'])


def get_simple_test_frames(preprocess):
    input_frame = get_simple_preprocessed_input_test_frame() if preprocess else get_simple_input_test_frame()

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


def tf_idf_with_preprocessing(): 
    return tf_idf_small_data(True)


def tf_idf_without_preprocessing(): 
    return tf_idf_small_data(True)


TESTS = [tf_idf_with_preprocessing, tf_idf_without_preprocessing]

if __name__ == "__main__":
    for func in TESTS:
        pyunit_utils.standalone_test(func)
else:
    for func in TESTS:
        func()
