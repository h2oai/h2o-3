from h2o.automl._base import H2OAutoMLBaseMixin
from h2o.grid import H2OGridSearch
from h2o.model.model_base import ModelBase
from h2o.utils.typechecks import is_type
from h2o.frame import H2OFrame
from h2o.expr import ExprNode
from h2o.exceptions import H2OValueError


def make_leaderboard(object, leaderboard_frame=None,
                     sort_metric="AUTO",
                     extra_columns=[],
                     scoring_data="AUTO"):

    def _get_models(obj):
        if isinstance(obj, list):
            result = []
            for o in obj:
                res = _get_models(o)
                if isinstance(res, list):
                    result.extend(res)
                else:
                    result.append(res)
            return result
        elif isinstance(obj, H2OAutoMLBaseMixin):
            return [row[0] for row in obj.leaderboard.as_data_frame(use_pandas=False, header=False)]
        elif isinstance(obj, H2OGridSearch):
            return obj.model_ids
        elif isinstance(obj, ModelBase):
            return obj.model_id
        elif isinstance(is_type(obj, str)):
            return obj
        else:
            raise H2OValueError("Unsupported model_id!")

    model_ids = _get_models(object)
    assert is_type(model_ids, [str])

    if scoring_data.lower() not in ("auto", "train", "valid", "xval"):
        raise H2OValueError("Scoring data has to be set to one of \"AUTO\", \"train\", \"valid\", \"xval\".")

    m_frame = H2OFrame._expr(ExprNode(
        "makeLeaderboard",
        model_ids,
        leaderboard_frame.key if leaderboard_frame is not None else "",
        sort_metric,
        extra_columns,
        scoring_data))
    return m_frame