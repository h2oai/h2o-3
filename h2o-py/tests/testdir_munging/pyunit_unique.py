import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def pyunit_unique():

    iris = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))
    uniques = iris[4].unique()
    rows, cols = uniques.dim
    assert rows == 3 and cols == 1, "Expected 3 rows and 1 column, but got {0} rows and {1} column".format(rows,cols)
    assert "Iris-setosa" in uniques[0], "Expected Iris-setosa to be in the set of unique species, but it wasn't"
    assert "Iris-virginica" in uniques[0], "Expected Iris-virginica to be in the set of unique species, but it wasn't"
    assert "Iris-versicolor" in uniques[0], "Expected Iris-versicolor to be in the set of unique species, but it wasn't"

    fr = h2o.create_frame(rows=5, cols=1, time_fraction=1)
    assert fr.type(0) == "time"
    uf = fr.unique()
    assert uf.type(0) == "time"
    uf.refresh()
    assert uf.type(0) == "time"

    prostate = h2o.import_file(pyunit_utils.locate("smalldata/parser/csv2orc/prostate_NA.csv"))
    prostate["GLEASON"] = prostate["GLEASON"].asfactor()
    uniques = prostate["GLEASON"].unique(include_nas=True)
    uniques_without_nas = prostate["GLEASON"].unique()
    prostate_pandas = prostate.as_data_frame()
    uniques_pandas = prostate_pandas["GLEASON"].unique()
    assert uniques.nrows == len(uniques_pandas)
    assert uniques_without_nas.nrows == len(uniques_pandas) - 1

    # make sure domains are recalculated with each temp assign
    df_example = h2o.H2OFrame({'time': ['M','M','M','D','D','M','M','D'],
                               'amount': [1,4,5,0,0,1,3,0]})

    df_example['amount'] = df_example['amount'].asfactor()
    filtered = df_example[df_example['time']=='D', 'amount']
    uniques = filtered['amount'].unique()
    assert len(uniques) == 1
    assert uniques.as_data_frame().iat[0,0] == 0

if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_unique)
else:
    pyunit_unique()
