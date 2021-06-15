rest_api_version = 99
supervised_learning = False


def class_extensions():
    @property
    def aggregated_frame(self):
        if (self._model_json is not None
                and self._model_json.get("output", {}).get("output_frame", {}).get("name") is not None):
            out_frame_name = self._model_json["output"]["output_frame"]["name"]
            return H2OFrame.get_frame(out_frame_name)

    @property
    def mapping_frame(self):
        if self._model_json is None:
            return None
        mj = self._model_json
        if mj.get("output", {}).get("mapping_frame", {}).get("name") is not None:
            mapping_frame_name = mj["output"]["mapping_frame"]["name"]
            return H2OFrame.get_frame(mapping_frame_name)

extensions = dict(
    __class__=class_extensions
)

examples = dict(
    categorical_encoding="""
>>> df = h2o.create_frame(rows=10000,
...                       cols=10,
...                       categorical_fraction=0.6,
...                       integer_fraction=0,
...                       binary_fraction=0,
...                       real_range=100,
...                       integer_range=100,
...                       missing_fraction=0,
...                       factors=100,
...                       seed=1234)
>>> params = {"target_num_exemplars": 1000,
...           "rel_tol_num_exemplars": 0.5,
...           "categorical_encoding": "eigen"}
>>> agg = H2OAggregatorEstimator(**params)
>>> agg.train(training_frame=df)
>>> new_df = agg.aggregated_frame
>>> new_df
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> df = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> checkpoints_dir = tempfile.mkdtemp()
>>> model = H2OAggregatorEstimator(target_num_exemplars=500, 
...                                rel_tol_num_exemplars=0.3,
...                                export_checkpoints_dir=checkpoints_dir)
>>> model.train(training_frame=df)
>>> new_df = model.aggregated_frame
>>> new_df
>>> len(listdir(checkpoints_dir))
""",
    ignore_const_cols="""
>>> df = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> params = {"ignore_const_cols": False,
...           "target_num_exemplars": 500,
...           "rel_tol_num_exemplars": 0.3,
...           "transform": "standardize",
...           "categorical_encoding": "eigen"}
>>> model = H2OAggregatorEstimator(**params)
>>> model.train(training_frame=df)
>>> new_df = model.aggregated_frame
>>> new_df
""",
    num_iteration_without_new_exemplar="""
>>> df = h2o.create_frame(rows=10000,
...                       cols=10,
...                       categorical_fraction=0.6,
...                       integer_fraction=0,
...                       binary_fraction=0,
...                       real_range=100,
...                       integer_range=100,
...                       missing_fraction=0,
...                       factors=100,
...                       seed=1234)
>>> params = {"target_num_exemplars": 1000,
...           "rel_tol_num_exemplars": 0.5,
...           "categorical_encoding": "eigen",
...           "num_iteration_without_new_exemplar": 400}
>>> agg = H2OAggregatorEstimator(**params)
>>> agg.train(training_frame=df)
>>> new_df = agg.aggregated_frame
>>> new_df
""",
    rel_tol_num_exemplars="""
>>> df = h2o.create_frame(rows=10000,
...                       cols=10,
...                       categorical_fraction=0.6,
...                       integer_fraction=0,
...                       binary_fraction=0,
...                       real_range=100,
...                       integer_range=100,
...                       missing_fraction=0,
...                       factors=100,
...                       seed=1234)
>>> params = {"target_num_exemplars": 1000,
...           "rel_tol_num_exemplars": 0.5,
...           "categorical_encoding": "eigen",
...           "num_iteration_without_new_exemplar": 400}
>>> agg = H2OAggregatorEstimator(**params)
>>> agg.train(training_frame=df)
>>> new_df = agg.aggregated_frame
>>> new_df
""",
    save_mapping_frame="""
>>> df = h2o.create_frame(rows=10000,
...                       cols=10,
...                       categorical_fraction=0.6,
...                       integer_fraction=0,
...                       binary_fraction=0,
...                       real_range=100,
...                       integer_range=100,
...                       missing_fraction=0,
...                       factors=100,
...                       seed=1234)
>>> params = {"target_num_exemplars": 1000,
...           "rel_tol_num_exemplars": 0.5,
...           "categorical_encoding": "eigen",
...           "save_mapping_frame": True}
>>> agg = H2OAggregatorEstimator(**params)
>>> agg.train(training_frame=df)
>>> mapping_frame = agg.mapping_frame
>>> mapping_frame
""",
    target_num_exemplars="""
>>> df = h2o.create_frame(rows=10000,
...                       cols=10,
...                       categorical_fraction=0.6,
...                       integer_fraction=0,
...                       binary_fraction=0,
...                       real_range=100,
...                       integer_range=100,
...                       missing_fraction=0,
...                       factors=100,
...                       seed=1234)
>>> params = {"target_num_exemplars": 1000,
...           "rel_tol_num_exemplars": 0.5,
...           "categorical_encoding": "eigen",
...           "num_iteration_without_new_exemplar": 400}
>>> agg = H2OAggregatorEstimator(**params)
>>> agg.train(training_frame=df)
>>> new_df = agg.aggregated_frame
>>> new_df
""",
    training_frame="""
>>> df = h2o.create_frame(rows=10000,
...                       cols=10,
...                       categorical_fraction=0.6,
...                       integer_fraction=0,
...                       binary_fraction=0,
...                       real_range=100,
...                       integer_range=100,
...                       missing_fraction=0,
...                       factors=100,
...                       seed=1234)
>>> params = {"target_num_exemplars": 1000,
...           "rel_tol_num_exemplars": 0.5,
...           "categorical_encoding": "eigen",
...           "num_iteration_without_new_exemplar": 400}
>>> agg = H2OAggregatorEstimator(**params)
>>> agg.train(training_frame=df)
>>> new_df = agg.aggregated_frame
>>> new_df
""",
    transform="""
>>> df = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> params = {"ignore_const_cols": False,
...           "target_num_exemplars": 500,
...           "rel_tol_num_exemplars": 0.3,
...           "transform": "standardize",
...           "categorical_encoding": "eigen"}
>>> model = H2OAggregatorEstimator(**params)
>>> model.train(training_frame=df)
>>> new_df = model.aggregated_frame
"""
)
    
