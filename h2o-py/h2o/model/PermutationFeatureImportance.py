# -*- encoding: utf-8 -*-
"""
H2O Model Reliance.

:copyright: (c) 2020 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
import h2o
from h2o.frame import H2OFrame
from h2o.expr import ExprNode
from h2o.utils.shared_utils import can_use_pandas


def permutation_featue_importance(frame, model, use_pandas=True):


    # j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (model.model_id, frame.frame_id),
    #             data={"reconstruct_train": True, "reverse_transform": reverse_transform})
    # return h2o.get_frame(j["model_metrics"][0]["predictions"]["frame_id"]["name"])

    model_ = model._model_json["output"]

    if type(frame) is H2OFrame:
        m_frame = H2OFrame._expr(ExprNode("Perm_Feature_importance", frame, model))
    else:
        raise ValueError("Frame is not H2OFrame")

    if use_pandas and can_use_pandas():
        import pandas
        return pandas.DataFrame(m_frame)

    return m_frame
