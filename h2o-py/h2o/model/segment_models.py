# -*- encoding: utf-8 -*-
"""
H2O Segment Models.

:copyright: (c) 2020 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import

from h2o.base import Keyed
from h2o.frame import H2OFrame
from h2o.expr import ExprNode
from h2o.expr import ASTId

__all__ = ("H2OSegmentModels", )


class H2OSegmentModels(Keyed):
    """
    Collection of H2O Models built for each input segment.

    :param segment_models_id: identifier of this collection of Segment Models

    :example:
    >>> segment_models = h2o.model.segment_models.H2OSegmentModels(segment_models_id="my_sm_id")
    >>> segment_models.as_frame()
    """

    #-------------------------------------------------------------------------------------------------------------------
    # Construction
    #-------------------------------------------------------------------------------------------------------------------

    def __init__(self, segment_models_id=None):
        self._segment_models_id = segment_models_id

    @property
    def key(self):
        return self._segment_models_id

    def detach(self):
        self._segment_models_id = None

    def as_frame(self):
        """
        Converts this collection of models to a tabular representation.

        :returns: An H2OFrame, first columns identify the input segments, rest of the columns describe the built models. 
        """
        return H2OFrame._expr(expr=ExprNode("segment_models_as_frame", ASTId(self._segment_models_id)))._frame(fill_cache=True)
