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
        raise ValueError(f"TF-IDF cannot be computed for input of type '{input_type}'. H2OFrame input is required.")
    
    col_indices = []
    for col in [document_id_col, text_col]:
        col_type = type(col)

        if col_type is int:
            col_indices.append(col)
        elif col_type is str:
            col_indices.append(frame.names.index(col))
        else:
            raise ValueError(f"Invalid type to specify a column ('{col_type}'). Name or index of a column is required.")

    tf_idf_frame = H2OFrame._expr(ExprNode("tf-idf", frame, *col_indices, preprocess, case_sensitive))

    return tf_idf_frame
