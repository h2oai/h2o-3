from __future__ import print_function

import sys

sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator

#Analogous to GLRMTest.java#testSubset
#Test based on bug found in glrm_census_large.ipynb

def glrm_subset():
  acs_orig = h2o.upload_file(path=pyunit_utils.locate("bigdata/laptop/census/ACS_13_5YR_DP02_cleaned.zip"), col_types = (['enum'] + ['numeric']*149))
  
  acs_full = acs_orig.drop("ZCTA5")
  acs_model = H2OGeneralizedLowRankEstimator(k = 10,
                                            transform = 'STANDARDIZE',
                                            loss = 'Quadratic',
                                            regularization_x = 'Quadratic',
                                            regularization_y = 'L1',
                                            gamma_x = 0.25,
                                            gamma_y = 0.5,
                                            max_iterations = 1)
  
  acs_model.train(x = acs_full.names, training_frame= acs_full)
  zcta_arch_x = h2o.get_frame(acs_model._model_json['output']['representation_name'])
  print (zcta_arch_x)
  
  acs_zcta_col = acs_orig["ZCTA5"].asfactor()
  
  idx = ((acs_zcta_col == '10065') |   # Manhattan, NY (Upper East Side)\n",
     (acs_zcta_col == '11219') |   # Manhattan, NY (East Harlem)\n",
      (acs_zcta_col == '66753') |   # McCune, KS\n",
     (acs_zcta_col == '84104') |   # Salt Lake City, UT\n",
     (acs_zcta_col == '94086') |   # Sunnyvale, CA\n",
      (acs_zcta_col == '95014'))    # Cupertino, CA\n",
  
  print(zcta_arch_x[idx,[0,1]])

if __name__ == "__main__":
  pyunit_utils.standalone_test(glrm_subset)
else:
  glrm_subset()
