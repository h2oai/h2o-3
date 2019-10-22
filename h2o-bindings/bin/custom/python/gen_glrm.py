doc = dict(
    __class__="""
Builds a generalized low rank model of a H2O dataset.
"""
)

examples = dict(
    expand_user_y="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> rank = 3
>>> gx = 0.5
>>> gy = 0.5
>>> trans = "standardize"
>>> iris_glrm = H2OGeneralizedLowRankEstimator(k=rank,
...                                            loss="Quadratic",
...                                            gamma_x=gx,
...                                            gamma_y=gy,
...                                            transform=trans,
...                                            expand_user_y=False)
>>> iris_glrm.train(x=iris.names, training_frame=iris)
>>> iris_glrm.show()
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> checkpoints_dir = tempfile.mkdtemp()
>>> iris_glrm = H2OGeneralizedLowRankEstimator(k=3,
...                                            export_checkpoints_dir=checkpoints_dir,
...                                            seed=1234)
>>> iris_glrm.train(x=iris.names, training_frame=iris)
>>> len(listdir(checkpoints_dir))
""",
    gamma_x="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> rank = 3
>>> gx = 0.5
>>> gy = 0.5
>>> trans = "standardize"
>>> iris_glrm = H2OGeneralizedLowRankEstimator(k=rank,
...                                            loss="Quadratic",
...                                            gamma_x=gx,
...                                            gamma_y=gy,
...                                            transform=trans)
>>> iris_glrm.train(x=iris.names, training_frame=iris)
>>> iris_glrm.show()
""",
    gamma_y="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> rank = 3
>>> gx = 0.5
>>> gy = 0.5
>>> trans = "standardize"
>>> iris_glrm = H2OGeneralizedLowRankEstimator(k=rank,
...                                            loss="Quadratic",
...                                            gamma_x=gx,
...                                            gamma_y=gy,
...                                            transform=trans)
>>> iris_glrm.train(x=iris.names, training_frame=iris)
>>> iris_glrm.show()
""",
    ignore_const_cols="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> iris_glrm = H2OGeneralizedLowRankEstimator(k=3,
...                                            ignore_const_cols=False,
...                                            seed=1234)
>>> iris_glrm.train(x=iris.names, training_frame=iris)
>>> iris_glrm.show()
""",
    impute_original="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> rank = 3
>>> gx = 0.5
>>> gy = 0.5
>>> trans = "standardize"
>>> iris_glrm = H2OGeneralizedLowRankEstimator(k=rank,
...                                            loss="Quadratic",
...                                            gamma_x=gx,
...                                            gamma_y=gy,
...                                            transform=trans
...                                            impute_original=True)
>>> iris_glrm.train(x=iris.names, training_frame=iris)
>>> iris_glrm.show()
""",
    init="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> iris_glrm = H2OGeneralizedLowRankEstimator(k=3,
...                                            init="svd",
...                                            seed=1234) 
>>> iris_glrm.train(x=iris.names, training_frame=iris)
>>> iris_glrm.show()
""",
    init_step_size="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> iris_glrm = H2OGeneralizedLowRankEstimator(k=3,
...                                            init_step_size=2.5,
...                                            seed=1234) 
>>> iris_glrm.train(x=iris.names, training_frame=iris)
>>> iris_glrm.show()
""",
)
