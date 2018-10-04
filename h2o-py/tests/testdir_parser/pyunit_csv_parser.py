import h2o

from tests import pyunit_utils


def pyunit_csv_parser():
    airlines_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    assert airlines_frame.nrow == 24421 # 24,423 rows in total. Last row is empty, first one is header

    airquality_train = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airquality_train1.csv"))
    assert airquality_train.nrow == 77 # 79 rows in total. Last row is empty, first one is header

    airquality_train = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airquality_train1.csv"))
    assert airquality_train.nrow == 77 # 79 rows in total. Last row is empty, first one is header

    cars_train = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/cars_train.csv"))
    assert cars_train.nrow == 331 # 333 rows in total. Last row is empty, first one is header

    higgs_train = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/higgs_train_5k.csv"))
    assert higgs_train.nrow == 5000 # 5002 rows in total. Last row is empty, first one is header

    housing_train = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/housing_train.csv"))
    assert housing_train.nrow == 413 # 415 rows in total. Last row is empty, first one is header

    insurance_gamma_train = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/insurance_gamma_dense_train.csv"))
    assert insurance_gamma_train.nrow == 45 # 47 rows in total. Last row is empty, first one is header

    iris_train = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/iris.csv"))
    assert iris_train.nrow == 150 # 152 rows in total. Last row is empty, first one is header

    agaricus_train = h2o.import_file(path=pyunit_utils.locate("smalldata/xgboost/demo/data/agaricus.txt.train"))
    assert agaricus_train.nrow == 6513 # 6514 rows in total. Last row is empty, no header.

    featmap = h2o.import_file(path=pyunit_utils.locate("smalldata/xgboost/demo/data/featmap.txt"))
    assert featmap.nrow == 126 # 6514 rows in total. Last row is empty, no header.

    diabetes_train = h2o.import_file(path=pyunit_utils.locate("smalldata/diabetes/diabetes_train.csv"))
    assert diabetes_train.nrow == 50001 # 50,003 rows in total. Last row is empty, first one is header.

if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_csv_parser)
else:
    pyunit_csv_parser()
