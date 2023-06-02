from .utils.typechecks import is_type
from .frame import H2OFrame
from .expr import ExprNode
from .exceptions import H2OValueError


def make_leaderboard(object, leaderboard_frame=None,
                     sort_metric="AUTO",
                     extra_columns=[],
                     scoring_data="AUTO"):
    """
    Create a leaderboard from a list of models, grids and/or automls.

    :param object: List of models, automls, or grids; or just single automl/grid object.
    :param leaderboard_frame: Frame used for generating the metrics (optional).
    :param sort_metric:  Metric used for sorting the leaderboard.
    :param extra_columns: What extra columns should be calculated (might require leaderboard_frame). Use "ALL" for all available or list of extra columns.
    :param scoring_data: Metrics to be reported in the leaderboard ("xval", "train", or "valid"). Used if no leaderboard_frame is provided.
    :return: H2OFrame

    :examples:
        >>> import h2o
        >>> from h2o.grid.grid_search import H2OGridSearch
        >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        >>> h2o.init()
        >>> training_data = h2o.import_file("https://h2o-public-test-data.s3.amazonaws.com/smalldata/logreg/benign.csv")
        >>> hyper_parameters = {'alpha': [0.01,0.5],
        ...                     'lambda': [1e-5,1e-6]}
        >>> gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'),
        ...                    hyper_parameters)
        >>> gs.train(y=3, training_frame=training_data)
        >>> h2o.make_leaderboard(gs, training_data)
    """
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
        elif hasattr(obj, "leaderboard"):
            return [row[0] for row in obj.leaderboard.as_data_frame(use_pandas=False, header=False)]
        elif hasattr(obj, "model_ids"):
            return obj.model_ids
        elif hasattr(obj, "model_id"):
            return obj.model_id
        elif is_type(obj, str):
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
