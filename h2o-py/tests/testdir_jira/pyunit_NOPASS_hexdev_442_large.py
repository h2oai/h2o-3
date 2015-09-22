import sys
sys.path.insert(1, "../../")
import h2o, tests

def hexdev_422():

    fr = h2o.import_file(h2o.locate("bigdata/laptop/jira/z_repro.csv.gz"))
    fr[0] = fr[0].asfactor()

    rf = h2o.random_forest(x=fr[1:fr.ncol], y=fr[0], min_rows=1, ntrees=25, max_depth=45)

    h2o.download_pojo(rf)

if __name__ == "__main__":
    tests.run_test(sys.argv, hexdev_422)
