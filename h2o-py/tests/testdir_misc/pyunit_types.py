

import h2o, tests


def pyunit_types():

  pros = h2o.import_file(tests.locate("smalldata/prostate/prostate.csv"))
  types = pros.types
  print types

  pros[1] = pros[1].asfactor()

  types2 = pros.types

  print types2


pyunit_test = pyunit_types
