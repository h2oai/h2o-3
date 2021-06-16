supervised_learning = False


doc = dict(
    __class__="""Performs k-means clustering on an H2O dataset.""",
)

examples = dict(
    categorical_encoding="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
>>> predictors = ["AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"]
>>> train, valid = prostate.split_frame(ratios=[.8], seed=1234)
>>> encoding = "one_hot_explicit"
>>> pros_km = H2OKMeansEstimator(categorical_encoding=encoding,
...                              seed=1234)
>>> pros_km.train(x=predictors,
...               training_frame=train,
...               validation_frame=valid)
>>> pros_km.scoring_history()
""",
    estimate_k="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> iris['class'] = iris['class'].asfactor()
>>> predictors = iris.columns[:-1]
>>> train, valid = iris.split_frame(ratios=[.8], seed=1234)
>>> iris_kmeans = H2OKMeansEstimator(k=10,
...                                  estimate_k=True,
...                                  standardize=False,
...                                  seed=1234)
>>> iris_kmeans.train(x=predictors,
...                   training_frame=train,
...                   validation_frame=valid)
>>> iris_kmeans.scoring_history()
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> airlines = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip", destination_frame="air.hex")
>>> predictors = ["DayofMonth", "DayOfWeek"]
>>> checkpoints_dir = tempfile.mkdtemp()
>>> air_km = H2OKMeansEstimator(export_checkpoints_dir=checkpoints_dir,
...                             seed=1234)
>>> air_km.train(x=predictors, training_frame=airlines)
>>> len(listdir(checkpoints_dir))
""",
    fold_assignment="""
>>> ozone = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/ozone.csv")
>>> predictors = ["radiation","temperature","wind"]
>>> train, valid = ozone.split_frame(ratios=[.8], seed=1234)
>>> ozone_km = H2OKMeansEstimator(fold_assignment="Random",
...                               nfolds=5,
...                               seed=1234)
>>> ozone_km.train(x=predictors,
...                training_frame=train,
...                validation_frame=valid)
>>> ozone_km.scoring_history()
""",
    fold_column="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> fold_numbers = cars.kfold_column(n_folds=5, seed=1234)
>>> fold_numbers.set_names(["fold_numbers"])
>>> cars = cars.cbind(fold_numbers)
>>> print(cars['fold_numbers'])
>>> cars_km = H2OKMeansEstimator(seed=1234)
>>> cars_km.train(x=predictors,
...               training_frame=cars,
...               fold_column="fold_numbers")
>>> cars_km.scoring_history()
""",
    ignore_const_cols="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars["const_1"] = 6
>>> cars["const_2"] = 7
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_km = H2OKMeansEstimator(ignore_const_cols=True,
...                              seed=1234)
>>> cars_km.train(x=predictors,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_km.scoring_history()
""",
    init="""
>>> seeds = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/flow_examples/seeds_dataset.txt")
>>> predictors = seeds.columns[0:7]
>>> train, valid = seeds.split_frame(ratios=[.8], seed=1234)
>>> seeds_km = H2OKMeansEstimator(k=3,
...                               init='Furthest',
...                               seed=1234)
>>> seeds_km.train(x=predictors,
...                training_frame=train,
...                validation_frame= valid)
>>> seeds_km.scoring_history()
""",
    k="""
>>> seeds = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/flow_examples/seeds_dataset.txt")
>>> predictors = seeds.columns[0:7]
>>> train, valid = seeds.split_frame(ratios=[.8], seed=1234)
>>> seeds_km = H2OKMeansEstimator(k=3, seed=1234)
>>> seeds_km.train(x=predictors,
...                training_frame=train,
...                validation_frame=valid)
>>> seeds_km.scoring_history()
""",
    keep_cross_validation_fold_assignment="""
>>> ozone = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/ozone.csv")
>>> predictors = ["radiation","temperature","wind"]
>>> train, valid = ozone.split_frame(ratios=[.8], seed=1234)
>>> ozone_km = H2OKMeansEstimator(keep_cross_validation_fold_assignment=True,
...                               nfolds=5,
...                               seed=1234)
>>> ozone_km.train(x=predictors,
...                training_frame=train)
>>> ozone_km.scoring_history()
""",
    keep_cross_validation_models="""
>>> ozone = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/ozone.csv")
>>> predictors = ["radiation","temperature","wind"]
>>> train, valid = ozone.split_frame(ratios=[.8], seed=1234)
>>> ozone_km = H2OKMeansEstimator(keep_cross_validation_models=True,
...                               nfolds=5,
...                               seed=1234)
>>> ozone_km.train(x=predictors,
...                training_frame=train,
...                validation_frame=valid)
>>> ozone_km.scoring_history()
""",
    max_iterations="""
>>> benign = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
>>> predictors = ["AGMT","FNDX","HIGD","DEG","CHK",
...               "AGP1","AGMN","LIV","AGLP"]
>>> train, valid = benign.split_frame(ratios=[.8], seed=1234)
>>> benign_km = H2OKMeansEstimator(max_iterations=50)
>>> benign_km.train(x=predictors,
...                 training_frame=train,
...                 validation_frame=valid)
>>> benign_km.scoring_history()
""",
    max_runtime_secs="""
>>> benign = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
>>> predictors = ["AGMT","FNDX","HIGD","DEG","CHK",
...               "AGP1","AGMN","LIV","AGLP"]
>>> train, valid = benign.split_frame(ratios=[.8], seed=1234)
>>> benign_km = H2OKMeansEstimator(max_runtime_secs=10,
...                                seed=1234)
>>> benign_km.train(x=predictors,
...                 training_frame=train,
...                 validation_frame=valid)
>>> benign_km.scoring_history()
""",
    nfolds="""
>>> benign = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
>>> predictors = ["AGMT","FNDX","HIGD","DEG","CHK",
...               "AGP1","AGMN","LIV","AGLP"]
>>> train, valid = benign.split_frame(ratios=[.8], seed=1234)
>>> benign_km = H2OKMeansEstimator(nfolds=5, seed=1234)
>>> benign_km.train(x=predictors,
...                 training_frame=train,
...                 validation_frame=valid)
>>> benign_km.scoring_history()
""",
    score_each_iteration="""
>>> benign = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/benign.csv")
>>> predictors = ["AGMT","FNDX","HIGD","DEG","CHK",
...               "AGP1","AGMN","LIV","AGLP"]
>>> train, valid = benign.split_frame(ratios=[.8], seed=1234)
>>> benign_km = H2OKMeansEstimator(score_each_iteration=True,
...                                seed=1234)
>>> benign_km.train(x=predictors,
...                 training_frame=train,
...                 validation_frame=valid)
>>> benign_km.scoring_history()
""",
    seed="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
>>> predictors = ["AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"]
>>> train, valid = prostate.split_frame(ratios=[.8], seed=1234)
>>> pros_w_seed = H2OKMeansEstimator(seed=1234)
>>> pros_w_seed.train(x=predictors,
...                   training_frame=train,
...                   validation_frame=valid)
>>> pros_wo_seed = H2OKMeansEstimator()
>>> pros_wo_seed.train(x=predictors,
...                    training_frame=train,
...                    validation_frame=valid)
>>> pros_w_seed.scoring_history()
>>> pros_wo_seed.scoring_history()
""",
    standardize="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios=[.8])
>>> boston_km = H2OKMeansEstimator(standardize=True)
>>> boston_km.train(x=predictors,
...                 training_frame=train,
...                 validation_frame=valid)
>>> boston_km.scoring_history()
""",
    training_frame="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
>>> predictors = ["AGE", "RACE", "DPROS", "DCAPS",
...               "PSA", "VOL", "GLEASON"]
>>> train, valid = prostate.split_frame(ratios=[.8], seed=1234)
>>> pros_km = H2OKMeansEstimator(seed=1234)
>>> pros_km.train(x=predictors,
...               training_frame=train,
...               validation_frame=valid)
>>> pros_km.scoring_history()
""",
    user_points="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> iris['class'] = iris['class'].asfactor()
>>> predictors = iris.columns[:-1]
>>> train, valid = iris.split_frame(ratios=[.8], seed=1234)
>>> point1 = [4.9,3.0,1.4,0.2]
>>> point2 = [5.6,2.5,3.9,1.1]
>>> point3 = [6.5,3.0,5.2,2.0]
>>> points = h2o.H2OFrame([point1, point2, point3])
>>> iris_km = H2OKMeansEstimator(k=3,
...                              user_points=points,
...                              seed=1234)
>>> iris_km.train(x=predictors,
...               training_frame=iris,
...               validation_frame=valid)
>>> iris_kmeans.tot_withinss(valid=True)
""",
    validation_frame="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
>>> predictors = ["AGE", "RACE", "DPROS", "DCAPS",
...               "PSA", "VOL", "GLEASON"]
>>> train, valid = prostate.split_frame(ratios=[.8], seed=1234)
>>> pros_km = H2OKMeansEstimator(seed=1234)
>>> pros_km.train(x=predictors,
...               training_frame=train,
...               validation_frame=valid)
>>> pros_km.scoring_history()
""",
    keep_cross_validation_predictions="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
>>> predictors = ["AGE", "RACE", "DPROS", "DCAPS",
...               "PSA", "VOL", "GLEASON"]
>>> train, valid = prostate.split_frame(ratios=[.8], seed=1234)
>>> pros_km = H2OKMeansEstimator(keep_cross_validation_predictions=True,
...                              nfolds=5,
...                              seed=1234)
>>> pros_km.train(x=predictors,
...               training_frame=train,
...               validation_frame=valid)
>>> pros_km.scoring_history()
""",
    cluster_size_constraints="""
>>> iris_h2o = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris.csv")
>>> k=3
>>> start_points = h2o.H2OFrame(
...         [[4.9, 3.0, 1.4, 0.2],
...          [5.6, 2.5, 3.9, 1.1],
...          [6.5, 3.0, 5.2, 2.0]])
>>> kmm = H2OKMeansEstimator(k=k,
...                          user_points=start_points,
...                          standardize=True,
...                          cluster_size_constraints=[2, 5, 8],
...                          score_each_iteration=True)
>>> kmm.train(x=list(range(7)), training_frame=iris_h2o)
>>> kmm.scoring_history()
"""
)
