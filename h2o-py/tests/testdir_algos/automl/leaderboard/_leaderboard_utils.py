from __future__ import print_function

import h2o

all_algos = ["DeepLearning", "DRF", "GBM", "GLM", "XGBoost", "StackedEnsemble"]


def check_model_property(model_names, prop_name, present=True, actual_value=None, default_value=None):
    for mn in model_names:
        model = h2o.get_model(mn)
        if present:
            assert prop_name in model.params.keys(), ("missing {prop} in model {model}"
                                                      .format(prop=prop_name, model=mn))
            assert actual_value is None or model.params[prop_name]['actual'] == actual_value, (
                "actual value for {prop} in model {model} is {val}, expected {exp}"
                    .format(prop=prop_name, model=mn, val=model.params[prop_name]['actual'], exp=actual_value))
            assert default_value is None or model.params[prop_name]['default'] == default_value, (
                "default value for {prop} in model {model} is {val}, expected {exp}"
                    .format(prop=prop_name, model=mn, val=model.params[prop_name]['default'], exp=default_value))
        else:
            assert prop_name not in model.params.keys(), ("unexpected {prop} in model {model}"
                                                          .format(prop=prop_name, model=mn))


def check_leaderboard(aml, excluded_algos, expected_metrics, expected_sort_metric, expected_sorted_desc=False):
    print("AutoML leaderboard")
    leaderboard = aml.leaderboard
    print(leaderboard)
    # check that correct leaderboard columns exist
    expected_columns = (['model_id'] + expected_metrics)
    assert leaderboard.names == expected_columns, (
        "expected leaderboard columns to be {expected} but got {actual}"
            .format(expected=expected_columns, actual=leaderboard.names))

    model_ids = list(h2o.as_list(leaderboard['model_id'])['model_id'])
    assert len([a for a in excluded_algos if len([b for b in model_ids if a in b]) > 0]) == 0, (
        "leaderboard contains some excluded algos among {excluded}: {models}"
            .format(excluded=excluded_algos, models=model_ids))

    included_algos = list(set(all_algos) - set(excluded_algos)) + ([] if 'DRF' in excluded_algos else ['XRT'])
    assert len([a for a in included_algos if len([b for b in model_ids if a in b]) > 0]) == len(included_algos), (
        "leaderboard is missing some algos from {included}: {models}"
            .format(included=included_algos, models=model_ids))

    j_leaderboard = aml._state_json['leaderboard']
    if expected_sort_metric is not None:
        sort_metric = j_leaderboard['sort_metric']
        assert sort_metric == expected_sort_metric, (
            "expected leaderboard sorted by {expected} but was sorted by {actual}"
                .format(expected=expected_sort_metric, actual=sort_metric))
    if expected_sorted_desc is not None:
        sorted_desc = j_leaderboard['sort_decreasing']
        assert sorted_desc == expected_sorted_desc, (
            "expected leaderboard sorted {expected} but was sorted {actual}"
                .format(expected="desc" if expected_sorted_desc else "asc",
                        actual="desc" if sorted_desc else "asc"))

