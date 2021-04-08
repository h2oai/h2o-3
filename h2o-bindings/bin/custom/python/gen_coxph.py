def class_extensions():
    @property
    def baseline_hazard_frame(self):
        if (self._model_json is not None
                and self._model_json.get("output", {}).get("baseline_hazard", {}).get("name") is not None):
            baseline_hazard_name = self._model_json["output"]["baseline_hazard"]["name"]
            return H2OFrame.get_frame(baseline_hazard_name)

    @property
    def baseline_survival_frame(self):
        if (self._model_json is not None
                and self._model_json.get("output", {}).get("baseline_survival", {}).get("name") is not None):
            baseline_survival_name = self._model_json["output"]["baseline_survival"]["name"]
            return H2OFrame.get_frame(baseline_survival_name)

extensions = dict(
    __class__=class_extensions
)

doc = dict(
    __class__="""
Trains a Cox Proportional Hazards Model (CoxPH) on an H2O dataset.
"""
)

examples = dict(
    training_frame="""
>>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
>>> predictor = "age"
>>> response = "event"
>>> train, valid = heart.split_frame(ratios=[.8])
>>> heart_coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
...                                                  stop_column="stop")
>>> heart_coxph.train(x=predictor,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> heart_coxph.scoring_history()
""",
    ties="""
>>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
>>> predictor = "age"
>>> response = "event"
>>> train, valid = heart.split_frame(ratios=[.8])
>>> heart_coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
...                                                  stop_column="stop",
...                                                  ties="breslow")
>>> heart_coxph.train(x=predictor,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> heart_coxph.scoring_history()
""",
    stop_column="""
>>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
>>> predictor = "age"
>>> response = "event"
>>> train, valid = heart.split_frame(ratios=[.8])
>>> heart_coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
...                                                  stop_column="stop")
>>> heart_coxph.train(x=predictor,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> heart_coxph.scoring_history()
""",
    start_column="""
>>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
>>> predictor = "age"
>>> response = "event"
>>> train, valid = heart.split_frame(ratios=[.8])
>>> heart_coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
...                                                  stop_column="stop")
>>> heart_coxph.train(x=predictor,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> heart_coxph.scoring_history()
""",
    use_all_factor_levels="""
>>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
>>> predictor = "age"
>>> response = "event"
>>> heart_coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
...                                                  stop_column="stop",
...                                                  use_all_factor_levels=True)
>>> heart_coxph.train(x=predictor,
...                   y=response,
...                   training_frame=heart)
>>> heart_coxph.scoring_history()
""",
    offset_column="""
>>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
>>> predictor = "age"
>>> response = "event"
>>> heart_coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
...                                                  stop_column="stop",
...                                                  offset_column="transplant")
>>> heart_coxph.train(x=predictor,
...                   y=response,
...                   training_frame=heart)
>>> heart_coxph.scoring_history()
""",
    max_iterations="""
>>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
>>> predictor = "age"
>>> response = "event"
>>> heart_coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
...                                                  stop_column="stop",
...                                                  max_iterations=50)
>>> heart_coxph.train(x=predictor,
...                   y=response,
...                   training_frame=heart)
>>> heart_coxph.scoring_history()
""",
    lre_min="""
>>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
>>> predictor = "age"
>>> response = "event"
>>> heart_coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
...                                                  stop_column="stop",
...                                                  lre_min=5)
>>> heart_coxph.train(x=predictor,
...                   y=response,
...                   training_frame=heart)
>>> heart_coxph.scoring_history()
""",
    interaction_pairs="""
>>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
>>> predictor = "age"
>>> response = "event"
>>> interaction_pairs = [("start","stop")]
>>> heart_coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
...                                                  stop_column="stop",
...                                                  interaction_pairs=interaction_pairs)
>>> heart_coxph.train(x=predictor,
...                   y=response,
...                   training_frame=heart)
>>> heart_coxph.scoring_history()
""",
    interactions="""
>>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
>>> predictor = "age"
>>> response = "event"
>>> interactions = ['start','stop']
>>> heart_coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
...                                                  stop_column="stop",
...                                                  interactions=interactions)
>>> heart_coxph.train(x=predictor,
...                   y=response,
...                   training_frame=heart)
>>> heart_coxph.scoring_history()
""",
    interactions_only="""
>>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
>>> predictor = "age"
>>> response = "event"
>>> interactions = ['start','stop']
>>> heart_coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
...                                                  stop_column="stop",
...                                                  interactions_only=interactions)
>>> heart_coxph.train(x=predictor,
...                   y=response,
...                   training_frame=heart)
>>> heart_coxph.scoring_history()
""",
    init="""
>>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
>>> predictor = "age"
>>> response = "event"
>>> heart_coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
...                                                  stop_column="stop",
...                                                  init=2.9)
>>> heart_coxph.train(x=predictor,
...                   y=response,
...                   training_frame=heart)
>>> heart_coxph.scoring_history()
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
>>> predictor = "age"
>>> response = "event"
>>> checkpoints_dir = tempfile.mkdtemp()
>>> coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
...                                            stop_column="stop",
...                                            export_checkpoints_dir=checkpoints_dir)
>>> coxph.train(x=predictor,
...             y=response,
...             training_frame=heart)
>>> len(listdir(checkpoints_dir))
"""
)
