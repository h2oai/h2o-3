# -*- encoding: utf-8 -*-
"""
H2O TargetEncoder.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

from h2o.expr import ExprNode

__all__ = ("TargetEncoder", )

class TargetEncoder(object):

    """
    Description goes here
    """

    #-------------------------------------------------------------------------------------------------------------------
    # Construction
    #-------------------------------------------------------------------------------------------------------------------

    def __init__(self, teColumns=None, targetColumnName = None, foldColumnName = None, blending = True):

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


    def fit(self, trainingFrame = None ):
        """
        Description of parameters:
        """
        print("Blending value is set to " + str(self._blending))
        return ExprNode("target.encoder.fit", trainingFrame, self._teColumns, self._targetColumnName, self._foldColumnName)._eager_map_frame() # we need another method for getting map instead of scalar.