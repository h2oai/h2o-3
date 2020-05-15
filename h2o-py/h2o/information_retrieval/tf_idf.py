# -*- encoding: utf-8 -*-
"""
TF-IDF
:copyright: (c) 2020 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""

from ..expr import ExprNode
from ..frame import H2OFrame


def tf_idf(frame, preprocess=True):
    """
    Computes TF-IDF values for each word in given documents.

    :param frame:       documents or words frame for which TF-IDF values should be computed. 
                        <p>
                        (Default) Row format when <code>preprocess = True</code>: documentID, documentString
                        </p>
                        <p>
                        Row format when <code>preprocess = False</code>: documentID, word
                        </p>
    :param preprocess:  whether input frame should be pre-processed. Defaults to <code>True</code>.

    :return:    resulting frame with TF-IDF values. 
                Row format: documentID, word, TF, IDF, TF-IDF
    """

    tf_idf_frame = H2OFrame._expr(ExprNode("tf-idf", frame, preprocess))

    return tf_idf_frame
