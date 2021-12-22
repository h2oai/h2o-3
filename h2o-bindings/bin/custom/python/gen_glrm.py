supervised_learning = False


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
    k="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> iris_glrm = H2OGeneralizedLowRankEstimator(k=3)
>>> iris_glrm.train(x=iris.names, training_frame=iris)
>>> iris_glrm.show()
""",
    representation_name="""
>>> acs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/census/ACS_13_5YR_DP02_cleaned.zip")
>>> acs_fill = acs.drop("ZCTA5")
>>> acs_glrm = H2OGeneralizedLowRankEstimator(k=10,
...                                           transform="standardize",
...                                           loss="quadratic",
...                                           regularization_x="quadratic",
...                                           regularization_y="L1",
...                                           gamma_x=0.25,
...                                           gamma_y=0.5,
...                                           max_iterations=1,
...                                           representation_name="acs_full")
>>> acs_glrm.train(x=acs_fill.names, training_frame=acs)
>>> acs_glrm.loading_name
>>> acs_glrm.show()
""",
    loading_name="""
>>> # loading_name will be deprecated.  Use representation_name instead.    
>>> acs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/census/ACS_13_5YR_DP02_cleaned.zip")
>>> acs_fill = acs.drop("ZCTA5")
>>> acs_glrm = H2OGeneralizedLowRankEstimator(k=10,
...                                           transform="standardize",
...                                           loss="quadratic",
...                                           regularization_x="quadratic",
...                                           regularization_y="L1",
...                                           gamma_x=0.25,
...                                           gamma_y=0.5,
...                                           max_iterations=1,
...                                           loading_name="acs_full")
>>> acs_glrm.train(x=acs_fill.names, training_frame=acs)
>>> acs_glrm.loading_name
>>> acs_glrm.show()
""",
    loss="""
>>> acs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/census/ACS_13_5YR_DP02_cleaned.zip")
>>> acs_fill = acs.drop("ZCTA5")
>>> acs_glrm = H2OGeneralizedLowRankEstimator(k=10,
...                                           transform="standardize",
...                                           loss="absolute",
...                                           regularization_x="quadratic",
...                                           regularization_y="L1",
...                                           gamma_x=0.25,
...                                           gamma_y=0.5,
...                                           max_iterations=700)
>>> acs_glrm.train(x=acs_fill.names, training_frame=acs)
>>> acs_glrm.show()
""",
    loss_by_col="""
>>> arrestsH2O = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/pca_test/USArrests.csv")
>>> arrests_glrm = H2OGeneralizedLowRankEstimator(k=3,
...                                               loss="quadratic",
...                                               loss_by_col=["absolute","huber"],
...                                               loss_by_col_idx=[0,3],
...                                               regularization_x="quadratic",
...                                               regularization_y="l1")
>>> arrests_glrm.train(x=arrestsH2O.names, training_frame=arrestsH2O)
>>> arrests_glrm.show()
""",
    loss_by_col_idx="""
>>> arrestsH2O = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/pca_test/USArrests.csv")
>>> arrests_glrm = H2OGeneralizedLowRankEstimator(k=3,
...                                               loss="quadratic",
...                                               loss_by_col=["absolute","huber"],
...                                               loss_by_col_idx=[0,3],
...                                               regularization_x="quadratic",
...                                               regularization_y="l1")
>>> arrests_glrm.train(x=arrestsH2O.names, training_frame=arrestsH2O)
>>> arrests_glrm.show()
""",
    max_iterations="""
>>> acs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/census/ACS_13_5YR_DP02_cleaned.zip")
>>> acs_fill = acs.drop("ZCTA5")
>>> acs_glrm = H2OGeneralizedLowRankEstimator(k=10,
...                                           transform="standardize",
...                                           loss="quadratic",
...                                           regularization_x="quadratic",
...                                           regularization_y="L1",
...                                           gamma_x=0.25,
...                                           gamma_y=0.5,
...                                           max_iterations=700)
>>> acs_glrm.train(x=acs_fill.names, training_frame=acs)
>>> acs_glrm.show()
""",
    max_runtime_secs="""
>>> arrestsH2O = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/pca_test/USArrests.csv")
>>> arrests_glrm = H2OGeneralizedLowRankEstimator(k=3,
...                                               max_runtime_secs=15,
...                                               max_iterations=500,
...                                               max_updates=900,
...                                               min_step_size=0.005)
>>> arrests_glrm.train(x=arrestsH2O.names, training_frame=arrestsH2O)
>>> arrests_glrm.show()
""",
    max_updates="""
>>> arrestsH2O = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/pca_test/USArrests.csv")
>>> arrests_glrm = H2OGeneralizedLowRankEstimator(k=3,
...                                               max_runtime_secs=15,
...                                               max_iterations=500,
...                                               max_updates=900,
...                                               min_step_size=0.005)
>>> arrests_glrm.train(x=arrestsH2O.names, training_frame=arrestsH2O)
>>> arrests_glrm.show()
""",
    min_step_size="""
>>> arrestsH2O = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/pca_test/USArrests.csv")
>>> arrests_glrm = H2OGeneralizedLowRankEstimator(k=3,
...                                               max_runtime_secs=15,
...                                               max_iterations=500,
...                                               max_updates=900,
...                                               min_step_size=0.005)
>>> arrests_glrm.train(x=arrestsH2O.names, training_frame=arrestsH2O)
>>> arrests_glrm.show()
""",
    multi_loss="""
>>> arrestsH2O = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/pca_test/USArrests.csv")
>>> arrests_glrm = H2OGeneralizedLowRankEstimator(k=3,
...                                               loss="quadratic",
...                                               loss_by_col=["absolute","huber"],
...                                               loss_by_col_idx=[0,3],
...                                               regularization_x="quadratic",
...                                               regularization_y="l1"
...                                               multi_loss="ordinal")
>>> arrests_glrm.train(x=arrestsH2O.names, training_frame=arrestsH2O)
>>> arrests_glrm.show()
""",
    period="""
>>> arrestsH2O = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/pca_test/USArrests.csv")
>>> arrests_glrm = H2OGeneralizedLowRankEstimator(k=3,
...                                               max_runtime_secs=15,
...                                               max_iterations=500,
...                                               max_updates=900,
...                                               min_step_size=0.005,
...                                               period=5)
>>> arrests_glrm.train(x=arrestsH2O.names, training_frame=arrestsH2O)
>>> arrests_glrm.show()
""",
    recover_svd="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_cat.csv")
>>> prostate[0] = prostate[0].asnumeric()
>>> prostate[4] = prostate[4].asnumeric()
>>> loss_all = ["Hinge", "Quadratic", "Categorical", "Categorical",
...             "Hinge", "Quadratic", "Quadratic", "Quadratic"]
>>> pros_glrm = H2OGeneralizedLowRankEstimator(k=5,
...                                            loss_by_col=loss_all,
...                                            recover_svd=True,
...                                            transform="standardize",
...                                            seed=12345)
>>> pros_glrm.train(x=prostate.names, training_frame=prostate)
>>> pros_glrm.show()
""",
    regularization_x="""
>>> arrestsH2O = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/pca_test/USArrests.csv")
>>> arrests_glrm = H2OGeneralizedLowRankEstimator(k=3,
...                                               loss="quadratic",
...                                               loss_by_col=["absolute","huber"],
...                                               loss_by_col_idx=[0,3],
...                                               regularization_x="quadratic",
...                                               regularization_y="l1")
>>> arrests_glrm.train(x=arrestsH2O.names, training_frame=arrestsH2O)
>>> arrests_glrm.show()
""",
    regularization_y="""
>>> arrestsH2O = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/pca_test/USArrests.csv")
>>> arrests_glrm = H2OGeneralizedLowRankEstimator(k=3,
...                                               loss="quadratic",
...                                               loss_by_col=["absolute","huber"],
...                                               loss_by_col_idx=[0,3],
...                                               regularization_x="quadratic",
...                                               regularization_y="l1")
>>> arrests_glrm.train(x=arrestsH2O.names, training_frame=arrestsH2O)
>>> arrests_glrm.show()
""",
    score_each_iteration="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_cat.csv")
>>> prostate[0] = prostate[0].asnumeric()
>>> prostate[4] = prostate[4].asnumeric()
>>> loss_all = ["Hinge", "Quadratic", "Categorical", "Categorical",
...             "Hinge", "Quadratic", "Quadratic", "Quadratic"]
>>> pros_glrm = H2OGeneralizedLowRankEstimator(k=5,
...                                            loss_by_col=loss_all,
...                                            score_each_iteration=True,
...                                            transform="standardize",
...                                            seed=12345)
>>> pros_glrm.train(x=prostate.names, training_frame=prostate)
>>> pros_glrm.show()
""",
    seed="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_cat.csv")
>>> prostate[0] = prostate[0].asnumeric()
>>> prostate[4] = prostate[4].asnumeric()
>>> glrm_w_seed = H2OGeneralizedLowRankEstimator(k=5, seed=12345) 
>>> glrm_w_seed.train(x=prostate.names, training_frame=prostate)
>>> glrm_wo_seed = H2OGeneralizedLowRankEstimator(k=5, 
>>> glrm_wo_seed.train(x=prostate.names, training_frame=prostate)
>>> glrm_w_seed.show()
>>> glrm_wo_seed.show()
""",
    svd_method="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_cat.csv")
>>> prostate[0] = prostate[0].asnumeric()
>>> prostate[4] = prostate[4].asnumeric()
>>> pros_glrm = H2OGeneralizedLowRankEstimator(k=5,
...                                            svd_method="power",
...                                            seed=1234)
>>> pros_glrm.train(x=prostate.names, training_frame=prostate)
>>> pros_glrm.show()
""",
    training_frame="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_cat.csv")
>>> prostate[0] = prostate[0].asnumeric()
>>> prostate[4] = prostate[4].asnumeric()
>>> pros_glrm = H2OGeneralizedLowRankEstimator(k=5,
...                                            seed=1234)
>>> pros_glrm.train(x=prostate.names, training_frame=prostate)
>>> pros_glrm.show()
""",
    transform="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_cat.csv")
>>> prostate[0] = prostate[0].asnumeric()
>>> prostate[4] = prostate[4].asnumeric()
>>> pros_glrm = H2OGeneralizedLowRankEstimator(k=5,
...                                            score_each_iteration=True,
...                                            transform="standardize",
...                                            seed=12345)
>>> pros_glrm.train(x=prostate.names, training_frame=prostate)
>>> pros_glrm.show()
""",
    user_x="""
>>> arrestsH2O = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> initial_x = ([[5.412, 65.24, -7.54, -0.032, 2.212, 92.24, -17.54, 23.268, 0.312,
...                123.24, 14.46, 9.768, 1.012, 19.24, -15.54, -1.732, 5.412, 65.24,
...                -7.54, -0.032, 2.212, 92.24, -17.54, 23.268, 0.312, 123.24, 14.46,
...                9.76, 1.012, 19.24, -15.54, -1.732, 5.412, 65.24, -7.54, -0.032,
...                2.212, 92.24, -17.54, 23.268, 0.312, 123.24, 14.46, 9.768, 1.012,
...                19.24, -15.54, -1.732, 5.412, 65.24]]*4)
>>> initial_x_h2o = h2o.H2OFrame(list(zip(*initial_x)))
>>> arrests_glrm = H2OGeneralizedLowRankEstimator(k=4,
...                                               transform="demean",
...                                               loss="quadratic",
...                                               gamma_x=0.5,
...                                               gamma_y=0.3,
...                                               init="user",
...                                               user_x=initial_x_h2o,
...                                               recover_svd=True)
>>> arrests_glrm.train(x=arrestsH2O.names, training_frame=arrestsH2O)
>>> arrests_glrm.show()
""",
    user_y="""
>>> arrestsH2O = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
>>> initial_y = [[5.412,  65.24,  -7.54, -0.032],
...              [2.212,  92.24, -17.54, 23.268],
...              [0.312, 123.24,  14.46,  9.768],
...              [1.012,  19.24, -15.54, -1.732]]
>>> initial_y_h2o = h2o.H2OFrame(list(zip(*initial_y)))
>>> arrests_glrm = H2OGeneralizedLowRankEstimator(k=4,
...                                               transform="demean",
...                                               loss="quadratic",
...                                               gamma_x=0.5,
...                                               gamma_y=0.3,
...                                               init="user",
...                                               user_y=initial_y_h2o,
...                                               recover_svd=True)
>>> arrests_glrm.train(x=arrestsH2O.names, training_frame=arrestsH2O)
>>> arrests_glrm.show()
""",
    validation_frame="""
>>> iris = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_wheader.csv")
>>> iris_glrm = H2OGeneralizedLowRankEstimator(k=3,
...                                            loss="quadratic",
...                                            gamma_x=0.5,
...                                            gamma_y=0.5,
...                                            transform="standardize")
>>> iris_glrm.train(x=iris.names,
...                 training_frame=iris,
...                 validation_frame=iris)
>>> iris_glrm.show()
"""
)
