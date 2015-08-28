import sys
sys.path.insert(1, "../../")
import h2o, tests

def spaces_in_column_names(ip,port):
    
    

    train_data = h2o.upload_file(path=h2o.locate("smalldata/jira/spaces_in_column_names.csv"))
    train_data.show()
    train_data.describe()
    X = ["p r e d i c t o r 1","predictor2","p r e d i ctor3","pre d ictor4","predictor5"]
    gbm = h2o.gbm(x=train_data[X], y=train_data["r e s p o n s e"].asfactor(), ntrees=1, distribution="bernoulli", min_rows=1)
    gbm.show()

if __name__ == "__main__":
    tests.run_test(sys.argv, spaces_in_column_names)
