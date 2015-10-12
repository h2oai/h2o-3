import sys
sys.path.insert(1,"../../../")
import h2o, tests

def anomaly():
    

    print "Deep Learning Anomaly Detection MNIST"

    train = h2o.import_file(tests.locate("bigdata/laptop/mnist/train.csv.gz"))
    test = h2o.import_file(tests.locate("bigdata/laptop/mnist/test.csv.gz"))

    predictors = range(0,784)
    resp = 784

    # unsupervised -> drop the response column (digit: 0-9)
    train = train[predictors]
    test = test[predictors]

    # 1) LEARN WHAT'S NORMAL
    # train unsupervised Deep Learning autoencoder model on train_hex
    from h2o.estimators.deeplearning import H2OAutoEncoderEstimator
    ae_model = H2OAutoEncoderEstimator(activation="Tanh", hidden=[2], l1=1e-5, ignore_const_cols=False, epochs=1)
    ae_model.train(X=predictors,training_frame=train)

    old_model_style = h2o.deeplearning(x=train[predictors],
                                       autoencoder=True,
                                       activation="Tanh",
                                       hidden=[2],
                                       l1=1e-5,
                                       ignore_const_cols=False,
                                       epochs=1
                                       )

    old_model_style.anomaly(test).show()

    # 2) DETECT OUTLIERS
    # anomaly app computes the per-row reconstruction error for the test data set
    # (passing it through the autoencoder model and computing mean square error (MSE) for each row)
    test_rec_error = ae_model.anomaly(test)

    # 3) VISUALIZE OUTLIERS
    # Let's look at the test set points with low/median/high reconstruction errors.
    # We will now visualize the original test set points and their reconstructions obtained
    # by propagating them through the narrow neural net.

    # Convert the test data into its autoencoded representation (pass through narrow neural net)
    test_recon = ae_model.predict(test)

    # In python, the visualization could be done with tools like numpy/matplotlib or numpy/PIL

if __name__ == '__main__':
    tests.run_test(sys.argv, anomaly)
