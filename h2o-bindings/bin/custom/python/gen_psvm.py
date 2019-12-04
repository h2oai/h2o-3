examples = dict(
    disable_training_metrics="""
>>> from h2o.estimators import H2OSupportVectorMachineEstimator
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(gamma=0.01,
...                                        rank_ratio=0.1,
...                                        disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=splice)
>>> svm.mse()
""",
    fact_threshold="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(disable_training_metrics=False,
...                                        fact_threshold=1e-7)
>>> svm.train(y="C1", training_frame=splice)
>>> svm.mse()
""",
    feasible_threshold="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(disable_training_metrics=False,
...                                        fact_threshold=1e-7)
>>> svm.train(y="C1", training_frame=splice)
>>> svm.mse()
""",
    gamma="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(gamma=0.01,
...                                        rank_ratio=0.1,
...                                        disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=splice)
>>> svm.mse()
""",
    hyper_param="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(gamma=0.01,
...                                        rank_ratio=0.1,
...                                        hyper_param=0.01,
...                                        disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=splice)
>>> svm.mse()
""",
    ignore_const_cols="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(gamma=0.01,
...                                        rank_ratio=0.1,
...                                        ignore_const_cols=False,
...                                        disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=splice)
>>> svm.mse()
""",
    kernel_type="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(gamma=0.1,
...                                        rank_ratio=0.1,
...                                        hyper_param=0.01,
...                                        kernel_type="gaussian",
...                                        disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=splice) 
>>> svm.mse()
""",
    max_iterations="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(gamma=0.1,
...                                        rank_ratio=0.1,
...                                        hyper_param=0.01,
...                                        max_iterations=20,
...                                        disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=splice)  
>>> svm.mse()
""",
    mu_factor="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(gamma=0.1,
...                                        mu_factor=100.5,
...                                        disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=splice) 
>>> svm.mse()
""",
    negative_weight="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(gamma=0.1,
...                                        rank_ratio=0.1,
...                                        negative_weight=10,
...                                        disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=splice)  
>>> svm.mse()
""",
    positive_weight="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(gamma=0.1,
...                                        rank_ratio=0.1,
...                                        positive_weight=0.1,
...                                        disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=splice)   
>>> svm.mse()
""",
    rank_ratio="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(gamma=0.01,
...                                        rank_ratio=0.1,
...                                        disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=splice)
>>> svm.mse()
""",
    seed="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(gamma=0.1,
...                                        rank_ratio=0.1,
...                                        seed=1234,
...                                        disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=splice)
>>> svm.model_performance
""",
    surrogate_gap_threshold="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(gamma=0.01,
...                                        rank_ratio=0.1,
...                                        surrogate_gap_threshold=0.1,
...                                        disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=splice) 
>>> svm.mse()
""",
    sv_threshold="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> svm = H2OSupportVectorMachineEstimator(gamma=0.01,
...                                        rank_ratio=0.1,
...                                        sv_threshold=0.01,
...                                        disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=splice) 
>>> svm.mse()
""",
    training_frame="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> train, valid = splice.split_frame(ratios=[0.8])
>>> svm = H2OSupportVectorMachineEstimator(disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=train)
>>> svm.mse()
""",
    validation_frame="""
>>> splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")
>>> train, valid = splice.split_frame(ratios=[0.8])
>>> svm = H2OSupportVectorMachineEstimator(disable_training_metrics=False)
>>> svm.train(y="C1", training_frame=train, validation_frame=valid)
>>> svm.mse()
"""
)
    
