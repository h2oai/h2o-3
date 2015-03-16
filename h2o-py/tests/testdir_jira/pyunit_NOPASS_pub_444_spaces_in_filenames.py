import sys
sys.path.insert(1, "../../")
import h2o

def pub_444_spaces_in_filenames(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    train_data = h2o.import_frame(path=h2o.locate("smalldata/jira/pub 444.csv"))
    train_data.show()
    train_data.describe()

    gbm = h2o.gbm(x=train_data[1:], y=train_data["response"], ntrees=1, loss="bernoulli")
    gbm.show()

if __name__ == "__main__":
    h2o.run_test(sys.argv, pub_444_spaces_in_filenames)