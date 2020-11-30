# -*- encoding: utf-8 -*-
"""
TF-IDF
:copyright: (c) 2020 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""

from ..expr import ExprNode
from ..frame import H2OFrame


def tf_idf(frame, document_id_col, text_col, preprocess=True, case_sensitive=True):
    """
    Computes TF-IDF values for each word in given documents.

    :param frame:           documents or words frame for which TF-IDF values should be computed.
    :param document_id_col: index or name of a column containing document IDs.
    :param text_col:        index or name of a column containing documents if <code>preprocess = True</code>
                            or words if <code>preprocess = False</code>.
    :param preprocess:      whether input text data should be pre-processed. Defaults to <code>True</code>.
    :param case_sensitive:  whether input data should be treated as case sensitive. Defaults to <code>True</code>.

    :return:    resulting frame with TF-IDF values. 
                Row format: documentID, word, TF, IDF, TF-IDF
    """
    input_type = type(frame)
    if input_type is not H2OFrame:
        raise ValueError("TF-IDF cannot be computed for input of type"+input_type+". H2OFrame input is required.")
    
    if type(document_id_col) is str:
        document_id_col = frame.names.index(document_id_col)
    elif type(document_id_col) is not int:
        raise ValueError("Name or index of the 'document_id_col' column is required. Invalid type "+type(document_id_col)+".")

    if type(text_col) is str:
        text_col = frame.names.index(text_col)
    elif type(text_col) is not int:
        raise ValueError("Name or index of the 'text_col' column is required. Invalid type "+type(text_col)+".")
    
    tf_idf_frame = H2OFrame._expr(ExprNode("tf-idf", frame, document_id_col, text_col, preprocess, case_sensitive))
    return tf_idf_frame
