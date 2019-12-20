import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
##
# Test out the cor() functionality
# If NAs in the frame, they are skipped in calculation unless na.rm = F
# If any categorical columns, throw an error
##

import numpy as np
import pandas as pd

def cor_test():
    iris_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    iris_np = np.genfromtxt(pyunit_utils.locate("smalldata/iris/iris.csv"),
                            delimiter=',',
                            skip_header=-1,
                            usecols=(0, 1, 2, 3))
    # Pearson
    print("Pearson")
    cor_np = h2o.H2OFrame(np.corrcoef(iris_np, rowvar=0))
    cor_h2o = iris_h2o[0:4].cor()
    cor_diff = abs(cor_h2o - cor_np)

    print("Correlation matrix with H2O: ")
    print(cor_h2o)

    print("Correlation matrix with Numpy: ")
    print(cor_np)

    print("Correlation differences between H2O and Numpy: ")
    print(cor_diff)

    print("Max difference in correlation calculation between H2O and Numpy: ")
    print(cor_diff.max())

    max = cor_diff.max()
    assert max < .00000000001, "expected equal correlations"

    # Spearman
    print("Spearman")
    # Rank using Pandas, then the calculation is done in the very same way as Pearson's, only on ranked cols
    ranked = pd.read_csv(pyunit_utils.locate("smalldata/iris/iris.csv"), header=-1, usecols=[0, 1, 2, 3]) \
        .rank(axis=0, method="min")
    cor_np = h2o.H2OFrame(np.corrcoef(ranked.as_matrix(), rowvar=0))
    print("\n NumPy:")
    print(cor_np)
    cor_h2o = iris_h2o[0:4].cor(measure="Spearman")
    print("\n H2O:")
    print(cor_h2o)
    cor_diff = abs(cor_h2o - cor_np)

    max = cor_diff.max()
    assert max < .00000000001, "expected equal correlations"

if __name__ == "__main__":
    pyunit_utils.standalone_test(cor_test)
else:
    cor_test()