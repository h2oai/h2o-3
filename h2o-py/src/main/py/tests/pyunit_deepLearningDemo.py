import sys
import h2o
from h2o import H2OGBM
from h2o import H2ODeeplearning


def deepLearningDemo(ip, port):

    h2o.init(ip, port)

    # Training data
    train_data = h2o.import_frame(path="smalldata/gbm_test/ecology_model.csv")
    train_data = train_data.drop('Site')
    train_data['Angaus'] = train_data['Angaus'].asfactor()
    print train_data.describe()
    train_data.head()

    # Testing data
    test_data = h2o.import_frame(path="smalldata/gbm_test/ecology_eval.csv")
    test_data['Angaus'] = test_data['Angaus'].asfactor()
    print test_data.describe()
    test_data.head()


    # Run GBM
    gbm = H2OGBM(training_frame=train_data, validation_frame=test_data, y="Angaus",
                 x=[name for name in train_data.names() if name != "Angaus"],
                 ntrees=100)

    gbm.fit()

    gbm.show()

    # Run DeepLearning

    dl = H2ODeeplearning()
    dl.training_frame = train_data
    dl.x = [name for name in train_data.names() if name != "Angaus"]
    dl.y = "Angaus"
    dl.epochs = 1000
    dl.hidden = [20, 20, 20]


    dl.fit()

    dl.show()

if __name__ == "__main__":
    args = sys.argv
    print args
    if len(args) > 1:
        ip = args[1]
        port = int(args[2])
    else:
        ip = "localhost"
        port = 54321
    deepLearningDemo(ip, port)