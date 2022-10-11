import h2o


def _train_and_get_models(model_class, x, y, train, **kwargs):
    from h2o.automl import H2OAutoML
    from h2o.grid import H2OGridSearch

    model = model_class(**kwargs)
    model.train(x, y, train)
    if model_class is H2OAutoML:
        return [h2o.get_model(m[0]) for m in model.leaderboard["model_id"].as_data_frame(False, False)]
    elif model_class is H2OGridSearch:
        return [h2o.get_model(m) for m in model.model_ids]
    else:
        return [model]


def infogram_grid(infogram, model_class, y, training_frame, **kwargs):
    """
    Runs grid over different feature subsets selected by infogram.

    :param infogram: H2OInfogram object
    :param model_class: H2O Estimator class, H2OAutoML, or H2OGridSearch
    :param y: response column
    :param training_frame: training frame
    :param kwargs: Arguments passed to the constructor of the model_class
    :return: list of H2O models
    """
    from h2o import H2OFrame
    from h2o.estimators import H2OInfogram
    from h2o.utils.typechecks import assert_is_type

    assert_is_type(infogram, H2OInfogram)
    assert hasattr(model_class, "train")
    assert_is_type(y, str)
    assert_is_type(training_frame, H2OFrame)

    safety = infogram.get_admissible_score_frame().sort("safety_index", False)
    cols = [x[0] for x in safety["column"].as_data_frame(False, False)]
    subsets = [cols[0:i] for i in range(1, len(cols)+1)]
    models = []
    for x in subsets:
        models.extend(_train_and_get_models(model_class, x, y, training_frame, **kwargs))
    return models
