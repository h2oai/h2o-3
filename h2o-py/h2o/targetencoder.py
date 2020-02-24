# -*- encoding: utf-8 -*-
"""
H2O TargetEncoder.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

from h2o.expr import ExprNode
from h2o.frame import H2OFrame
from h2o.utils.typechecks import (assert_is_type)
from h2o import get_frame
import warnings

__all__ = ("TargetEncoder", )

class TargetEncoder(object):

    """
    :deprecated:
    Use :func:`h2o.estimators.targetencoder.H2OTargetEncoderEstimator` instead.

    This is a main class that provides Python's API to the Java implementation of the target encoding.

    In general target encoding could be applied to three types of problems, namely:
    
         1) Binary classification (supported)
         2) Multi-class classification (not supported yet)
         3) Regression (not supported yet)

    :param List[str]-or-List[int] x: List of categorical column names or indices that we want apply target encoding to.
    :param str-or-int y: the name or column index of the response variable in the data.
    :param str-or-int fold_column: the name or column index of the fold column in the data.
    :param boolean blending_avg: (deprecated) whether to perform blended average. Defaults to TRUE.
    :param boolean blended_avg: whether to perform blended average. Defaults to TRUE.
    :param double inflection_point: parameter for blending. Used to calculate `lambda`. Determines half of the minimal sample size
        for which we completely trust the estimate based on the sample in the particular level of categorical variable. Default value is 10.
    :param double smoothing: parameter for blending. Used to calculate `lambda`. Controls the rate of transition between
        the particular level's posterior probability and the prior probability. For smoothing values approaching infinity it becomes a hard
        threshold between the posterior and the prior probability. Default value is 20.

    :examples:

    >>> targetEncoder = TargetEncoder(x=te_columns, y=responseColumnName, blended_avg=True, inflection_point=10, smoothing=20)
    >>> targetEncoder.fit(trainFrame) 
    >>> encodedTrain = targetEncoder.transform(frame=trainFrame, holdout_type="kfold", seed=1234, is_train_or_valid=True)
    >>> encodedValid = targetEncoder.transform(frame=validFrame, holdout_type="none", noise=0.0, is_train_or_valid=True)
    >>> encodedTest = targetEncoder.transform(frame=testFrame, holdout_type="none", noise=0.0, is_train_or_valid=False)

    """

    #-------------------------------------------------------------------------------------------------------------------
    # Construction
    #-------------------------------------------------------------------------------------------------------------------

    def __init__(self, x=None, y=None, fold_column='', blended_avg=True, inflection_point=10, smoothing=20, **kwargs):

        """
        Deprecated API. Please use H2OTargetencoderEstimator instead.
        
        Creates instance of the TargetEncoder class and setting parameters that will be used in both `train` and `transform` methods.
        """

        if(type(x) == str or type(x) == int):
            x = [x]
        self._teColumns = x
        self._responseColumnName = y
        self._foldColumnName = fold_column
        if 'blending_avg' in kwargs:
            warnings.warn("Parameter blending_avg is deprecated; use blended_avg instead", category=DeprecationWarning, stacklevel=2)
            self._blending = kwargs.get('blending_avg')
        else:
            self._blending = blended_avg
          
        if not inflection_point > 0:
            raise ValueError("Parameter `inflection_point` should be greater than 0")
        
        if not smoothing > 0:
            raise ValueError("Parameter `smoothing` should be greater than 0")

        self._inflectionPoint = inflection_point
        self._smoothing = smoothing


    def fit(self, frame = None):
        """
        Deprecated API. Please use H2OTargetencoderEstimator instead.
        
        Returns encoding map as an object that maps 'column_name' -> 'frame_with_encoding_map_for_this_column_name'

        :param frame frame: An H2OFrame object with which to create the target encoding map

        :examples:
        >>> targetEncoder = TargetEncoder(x=te_columns, y=responseColumnName, blended_avg=True, inflection_point=10, smoothing=20)
        >>> targetEncoder.fit(trainFrame) 
        """
        self._teColumns = list(map(lambda i: frame.names[i], self._teColumns)) if all(isinstance(n, int) for n in self._teColumns) else self._teColumns
        self._responseColumnName = frame.names[self._responseColumnName] if isinstance(self._responseColumnName, int) else self._responseColumnName
        self._foldColumnName = frame.names[self._foldColumnName] if isinstance(self._foldColumnName, int) else self._foldColumnName
        
        self._encodingMap = ExprNode("target.encoder.fit", frame, self._teColumns, self._responseColumnName,
                                     self._foldColumnName)._eager_map_frame()

        return self._encodingMap

    def transform(self, frame=None, holdout_type=None, noise=-1, seed=-1):
        """
        Deprecated API. Please use H2OTargetencoderEstimator instead.
        
        Apply transformation to `te_columns` based on the encoding maps generated during `TargetEncoder.fit()` call.
        You must not pass encodings manually from `.fit()` method because they are being stored internally
        after `.fit()' had been called.

        :param frame frame: to which frame we are applying target encoding transformations.
        :param str holdout_type: Supported options:

                1) "kfold" - encodings for a fold are generated based on out-of-fold data.
                2) "loo" - leave one out. Current row's response value is subtracted from the pre-calculated per-level frequencies.
                3) "none" - we do not holdout anything. Using whole frame for training
                
        :param float noise: the amount of random noise added to the target encoding.  This helps prevent overfitting. Defaults to 0.01 * range of y.
        :param int seed: a random seed used to generate draws from the uniform distribution for random noise. Defaults to -1.

        :example:
        >>> targetEncoder = TargetEncoder(x=te_columns, y=responseColumnName, blended_avg=True, inflection_point=10, smoothing=20)
        >>> encodedTrain = targetEncoder.transform(frame=trainFrame, holdout_type="kfold", seed=1234, is_train_or_valid=True)
        """
        assert_is_type(holdout_type, "kfold", "loo", "none")
        
        if holdout_type == "kfold" and self._foldColumnName == '' :
            raise ValueError("Attempt to use kfold strategy when encoding map was created without fold column being specified.")
        if holdout_type == "none" and noise != 0 :
            warnings.warn("Attempt to apply noise with holdout_type=`none` strategy", stacklevel=2)

        encodingMapKeys = self._encodingMap.map_keys['string']
        encodingMapFramesKeys = list(map(lambda x: x['key']['name'], self._encodingMap.frames))
        return H2OFrame._expr(expr=ExprNode("target.encoder.transform", encodingMapKeys, encodingMapFramesKeys, frame, self._teColumns, holdout_type,
                                            self._responseColumnName, self._foldColumnName,
                                            self._blending, self._inflectionPoint, self._smoothing,
                                            noise, seed))

    def encoding_map_frames(self):
        return list(map(lambda x: get_frame(x['key']['name']), self._encodingMap.frames))
