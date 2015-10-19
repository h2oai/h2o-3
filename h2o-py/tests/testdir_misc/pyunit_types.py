
import numpy as np

def pyunit_types():

  pros = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
  types = pros.types
  print types

  pros[1] = pros[1].asfactor()

  types2 = pros.types

  print types2

  df = h2o.H2OFrame(np.random.randn(100,4).tolist(), column_names=list("ABCD"), column_types=["Enum"]*4)
  assert df.types == {"A": "Enum", "C": "Enum", "B": "Enum", "D": "Enum"}, "Expected {} for column types " \
                      "but got {}".format({"A": "Enum", "C": "Enum", "B": "Enum", "D": "Enum"}, df.types)

  df = h2o.H2OFrame(np.random.randn(100,4).tolist())
  assert df.types == {"C3": "Numeric", "C2": "Numeric", "C1": "Numeric", "C4": "Numeric"}, "Expected {}" \
          " for column types but got {}".format({"C3": "Numeric", "C2": "Numeric", "C1": "Numeric",
                                                "C4": "Numeric"}, df.types)


pyunit_types()
