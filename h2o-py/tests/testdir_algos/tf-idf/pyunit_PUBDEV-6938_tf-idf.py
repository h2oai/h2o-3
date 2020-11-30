from collections import OrderedDict

from h2o import H2OFrame
from h2o.information_retrieval.tf_idf import tf_idf
from tests import pyunit_utils


def tf_idf_small_data(preprocess, case_sens, cols=None):
    if cols is None:
        cols = [0, 1]
    input_fr = get_simple_input_test_frame() if preprocess else get_simple_preprocessed_input_test_frame()
    expected_fr = get_expected_output_frame_case_sens() if case_sens else get_expected_output_frame_case_insens()
    out_frame = tf_idf(input_fr, cols[0], cols[1], preprocess, case_sens)
    pyunit_utils.compare_frames(expected_fr, out_frame, len(out_frame), tol_numeric=1e-5, compare_NA=False)
    

def get_simple_input_test_frame():
    doc_ids = [0, 1, 2]
    documents = [
        'A B C',
        'A a a Z',
        'C c B C'
    ]
    
    return H2OFrame(OrderedDict([('DocID', doc_ids), ('Document', documents)]),
                    column_types=['numeric', 'string'])


def get_simple_preprocessed_input_test_frame():
    doc_ids = [
        0, 0, 0, 
        1, 1, 1, 1,
        2, 2, 2, 2
    ]
    words = [
        'A', 'B', 'C',
        'A', 'a', 'a', 'Z',
        'C', 'c', 'B', 'C'
    ]
    
    return H2OFrame(OrderedDict([('DocID', doc_ids), ('Words', words)]),
                    column_types=['numeric', 'string'])


def get_expected_output_frame_case_insens():
    return get_expected_output_frame([0, 1, 0, 2, 0, 2, 1], 
                                     ['a', 'a', 'b', 'b', 'c', 'c', 'z'],
                                     [1, 3, 1, 1, 1, 3, 1],
                                     [0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.69314],
                                     [0.28768, 0.86304, 0.28768, 0.28768, 0.28768, 0.86304, 0.69314])


def get_expected_output_frame_case_sens():
    return get_expected_output_frame([0, 1, 0, 2, 0, 2, 1, 1, 2],
                                     ['A', 'A', 'B', 'B', 'C', 'C', 'Z', 'a', 'c'],
                                     [1, 1, 1, 1, 1, 2, 1, 2, 1],
                                     [0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.69314, 0.69314, 0.69314],
                                     [0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.57536, 0.69314, 1.38629, 0.69314])


def get_expected_output_frame(out_doc_ids, out_tokens, out_TFs, out_IDFs, out_TFIDFs):
    return H2OFrame(OrderedDict([('DocID', out_doc_ids), ('Token', out_tokens),
                                 ('TF', out_TFs), ('IDF', out_IDFs), ('TF_IDF', out_TFIDFs)]),
                     column_types=['numeric', 'string', 'numeric', 'numeric', 'numeric'])


def tf_idf_with_preprocessing_case_ins(): 
    return tf_idf_small_data(True, False, ['DocID', 'Document'])


def tf_idf_with_preprocessing_case_sens():
    return tf_idf_small_data(True, True)


def tf_idf_without_preprocessing_case_ins(): 
    return tf_idf_small_data(False, False, ['DocID', 'Words'])


def tf_idf_without_preprocessing_case_sens():
    return tf_idf_small_data(False, True)


TESTS = [tf_idf_with_preprocessing_case_ins,
         tf_idf_with_preprocessing_case_sens,
         tf_idf_without_preprocessing_case_ins,
         tf_idf_without_preprocessing_case_sens]

if __name__ == "__main__":
    for func in TESTS:
        pyunit_utils.standalone_test(func)
else:
    for func in TESTS:
        func()
