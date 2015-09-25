import sys, os
sys.path.insert(1, "../../")
import h2o, tests
import random

def pub_444_spaces_in_filenames():
    
    

    # tempdir = "smalldata/jira/"
    # if was okay to write to smalldata, it's okay to write to the current directory
    # probably don't want to, but can't find what the standard temp directory is supposed to be. no sandbox?
    tempdir = "./"
    # make a few files with spaces in the name
    f1 = open(h2o.locate(tempdir) + "foo .csv", "w")
    f1.write("response, predictor\n")
    for i in range(10):
        f1.write("1, a\n")
        f1.write("0, b\n")
        f1.write("1, a\n" if random.randint(0,1) else "0, b\n")
    f1.close()

    f2 = open(h2o.locate(tempdir) + "b a r .csv", "w")
    f2.write("response, predictor\n")
    for i in range(10):
        f2.write("1, a\n")
        f2.write("0, b\n")
        f2.write("1, a\n" if random.randint(0,1) else "0, b\n")
    f2.close()

    f3 = open(h2o.locate(tempdir) + " ba z.csv", "w")
    for i in range(10):
        f3.write("1, a\n")
        f3.write("0, b\n")
        f3.write("1, a\n" if random.randint(0,1) else "0, b\n")
    f3.close()

    train_data = h2o.upload_file(path=h2o.locate(tempdir + "foo .csv"))
    train_data.show()
    train_data.describe()
    gbm = h2o.gbm(x=train_data[1:], y=train_data["response"].asfactor(), ntrees=1, distribution="bernoulli", min_rows=1)
    gbm.show()

    train_data = h2o.upload_file(path=h2o.locate(tempdir + "b a r .csv"))
    train_data.show()
    train_data.describe()
    gbm = h2o.gbm(x=train_data[1:], y=train_data["response"].asfactor(), ntrees=1, distribution="bernoulli", min_rows=1)
    gbm.show()

    train_data = h2o.upload_file(path=h2o.locate(tempdir + " ba z.csv"))
    train_data.show()
    train_data.describe()
    gbm = h2o.gbm(x=train_data[1:], y=train_data[0].asfactor(), ntrees=1, distribution="bernoulli", min_rows=1)
    gbm.show()

    os.remove(h2o.locate(tempdir) + "foo .csv")
    os.remove(h2o.locate(tempdir) + "b a r .csv")
    os.remove(h2o.locate(tempdir) + " ba z.csv")

if __name__ == "__main__":
    tests.run_test(sys.argv, pub_444_spaces_in_filenames)
