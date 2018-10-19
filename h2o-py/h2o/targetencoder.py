# -*- encoding: utf-8 -*-
"""
H2O TargetEncoder.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

from h2o.expr import ExprNode
from h2o.frame import H2OFrame

__all__ = ("TargetEncoder", )

class TargetEncoder(object):

    """
    Description goes here
    """

    #-------------------------------------------------------------------------------------------------------------------
    # Construction
    #-------------------------------------------------------------------------------------------------------------------

    def __init__(self, teColumns=None, targetColumnName = None, foldColumnName = '', blending = True, inflection_point = 3, smoothing = 1):

        #todo remove (self, teColumns=None, destination_frame=None, header=0, separator=",", column_names=None, column_types=None, na_strings=None
        """
        Description:
        """
        #Validations:
        #coltype = U(None, "unknown", "uuid", "string", "float", "real", "double", "int", "numeric", "categorical", "factor", "enum", "time")
        # assert_is_type(python_obj, None, list, tuple, dict, numpy_ndarray, pandas_dataframe, scipy_sparse)
        # assert_is_type(destination_frame, None, str)
        # assert_is_type(header, -1, 0, 1)
        # assert_is_type(separator, I(str, lambda s: len(s) == 1))
        # assert_is_type(column_names, None, [str])
        # assert_is_type(column_types, None, [coltype], {str: coltype})
        # assert_is_type(na_strings, None, [str], [[str]], {str: [str]})

        self._teColumns = teColumns
        self._targetColumnName = targetColumnName
        self._foldColumnName = foldColumnName
        self._blending = blending
        self._inflectionPoint = inflection_point
        self._smoothing = smoothing


    def fit(self, trainingFrame = None ):
        """
        Description of the parameters:
        """
        self._encodingMap = ExprNode("target.encoder.fit", trainingFrame, self._teColumns, self._targetColumnName,
                         self._foldColumnName)._eager_map_frame()

        return self._encodingMap

    def transform(self, frame = None , strategy = None, noise = -1, seed = -1):
        """
        Description of the parameters:
        """
        # We need to make sure that frames are being sent in the same order
        assert self._encodingMap.teColumns['string'] == self._teColumns
        encodingMapKeys = self._encodingMap.teColumns['string']
        encodingMapFramesKeys = list(map(lambda x: x['key']['name'], self._encodingMap.frames))
        return H2OFrame._expr(expr=ExprNode("target.encoder.transform", encodingMapKeys, encodingMapFramesKeys, frame, self._teColumns, strategy,
                        self._targetColumnName, self._foldColumnName,
                        self._blending, self._inflectionPoint, self._smoothing,
                        noise, seed))