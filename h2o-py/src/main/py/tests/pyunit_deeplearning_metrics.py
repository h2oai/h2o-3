import sys
import h2o
from h2o import H2ODeeplearning

ip = "localhost"
port = 54321


def deep_learning_metrics_test():
    global ip, port
    h2o.init(ip, port)               # connect to existing cluster
    df = h2o.H2OFrame(remote_fname="smalldata/logreg/prostate.csv")

    del df['ID']                               # remove ID
    df['CAPSULE'] = df['CAPSULE'].asfactor()   # make CAPSULE categorical
    vol = df['VOL']
    vol[vol == 0] = None                       # 0 VOL means 'missing'

    r = vol.runif()                            # random train/test split
    train = df[r < 0.8]
    test  = df[r >= 0.8]

    # See that the data is ready
    print train.describe()
    train.head()
    print test.describe()
    test.head()

    # Run DeepLearning

    print "Train a Deeplearning model: "
    dl = H2ODeeplearning(training_frame=train,
                         x=range(2, train.ncol(), 1),
                         y="CAPSULE", epochs=100, hidden=[10, 10, 10])


    dl.fit()

    print "Binomial Model Metrics: "
    print
    dl.model_performance(test).show()


if __name__ == "__main__":
    args = sys.argv
    global ip
    global port
    if len(args) > 1:
        ip = args[1]
        port = args[2]
    deep_learning_metrics_test()