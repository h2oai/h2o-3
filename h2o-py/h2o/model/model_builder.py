# -*- encoding: utf-8 -*-
"""
Model builder.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""

# DELETE ME I'm useless !!!

from __future__ import absolute_import, division, print_function, unicode_literals
# noinspection PyUnresolvedReferences
from h2o.utils.compatibility import *  # NOQA

import h2o
from ..frame import H2OFrame
from ..job import H2OJob
from .model_future import H2OModelFuture
from .models.dim_reduction import H2ODimReductionModel
from .models.autoencoder import H2OAutoEncoderModel
from .models.multinomial import H2OMultinomialModel
from .models.ordinal import H2OOrdinalModel
from .models.regression import H2ORegressionModel
from .models.binomial import H2OBinomialModel
from .models.clustering import H2OClusteringModel
from .models.word_embedding import H2OWordEmbeddingModel


def build_model(algo_params):
    if algo_params["training_frame"] is None: raise ValueError("Missing training_frame")
    x = algo_params.pop("X")
    y = algo_params.pop("y", None)
    training_frame = algo_params.pop("training_frame")
    validation_frame = algo_params.pop("validation_frame", None)
    algo = algo_params.pop("algo")
    is_auto_encoder = algo_params is not None and "autoencoder" in algo_params and algo_params[
                                                                                       "autoencoder"] is not None
    is_unsupervised = is_auto_encoder or algo == "pca" or algo == "kmeans"
    if is_auto_encoder and y is not None: raise ValueError("y should not be specified for autoencoder.")
    if not is_unsupervised and y is None: raise ValueError("Missing response")
    return _model_build(x, y, training_frame, validation_frame, algo, algo_params)


def _model_build(x, y, tframe, vframe, algo, kwargs):
    kwargs['training_frame'] = tframe
    if vframe is not None: kwargs["validation_frame"] = vframe
    if y is not None:  kwargs['response_column'] = tframe[y].names[0]
    kwargs = dict(
        [(k, (kwargs[k]._frame()).frame_id if isinstance(kwargs[k], H2OFrame) else kwargs[k]) for k in kwargs if
         kwargs[k] is not None])  # gruesome one-liner
    rest_ver = kwargs.pop("_rest_version") if "_rest_version" in kwargs else 3
    future_model = H2OModelFuture(
        H2OJob(h2o.api("POST /%d/ModelBuilders/%s" % (rest_ver, algo), data=kwargs), job_type=(algo + " Model Build")),
        x)
    return _resolve_model(future_model, _rest_version=rest_ver, **kwargs)


def _resolve_model(future_model, **kwargs):
    future_model.poll()
    rest_ver = kwargs["_rest_version"] if "_rest_version" in kwargs else 3
    model_json = h2o.api("GET /%d/Models/%s" % (rest_ver, future_model.job.dest_key))["models"][0]

    model_type = model_json["output"]["model_category"]
    if model_type == "Binomial":
        model = H2OBinomialModel(future_model.job.dest_key, model_json)
    elif model_type == "Clustering":
        model = H2OClusteringModel(future_model.job.dest_key, model_json)
    elif model_type == "Regression":
        model = H2ORegressionModel(future_model.job.dest_key, model_json)
    elif model_type == "Multinomial":
        model = H2OMultinomialModel(future_model.job.dest_key, model_json)
    elif model_type == "Ordinal":
        model = H2OOrdinalModel(future_model.job.dest_key, model_json)
    elif model_type == "AutoEncoder":
        model = H2OAutoEncoderModel(future_model.job.dest_key, model_json)
    elif model_type == "DimReduction":
        model = H2ODimReductionModel(future_model.job.dest_key, model_json)
    elif model_type == "WordEmbedding":
        model = H2OWordEmbeddingModel(future_model.job.dest_key, model_json)
    else:
        raise NotImplementedError(model_type)
    return model
