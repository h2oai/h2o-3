# -*- encoding: utf-8 -*-
"""
TF-IDF
:copyright: (c) 2020 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""

from ..expr import ExprNode
from ..frame import H2OFrame


def tf_idf(frame):
    """
    Computes TF-IDF values for each word in given documents.

    :param frame:   documents frame for which TF-IDF values should be computed. 
                    Row format: documentID, documentString

    :return:    resulting frame with TF-IDF values. 
                Row format: documentID, word, TF, IDF, TF-IDF
    """

    tf_idf_frame = H2OFrame._expr(ExprNode("tf-idf", frame))

    return tf_idf_frame
