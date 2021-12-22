import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.anovaglm import H2OANOVAGLMEstimator

# Test to make sure anova table content is the same as model summary
def test_anova_table_frame():
  train = h2o.import_file(path=pyunit_utils.locate("smalldata/anovaGlm/Moore.csv"))
  y = 'conformity'
  x = ['fcategory', 'partner.status']

  model = H2OANOVAGLMEstimator(family='gaussian', lambda_=0, save_transformed_framekeys=True)
  model.train(x=x, y=y, training_frame=train)
  anova_table = model.result()
  # compare model summary and anova table frame
  colNames = anova_table.names
  for name in colNames:
    summaryCol = pyunit_utils.extract_col_value_H2OTwoDimTable(model._model_json["output"]["model_summary"], name)
    for ind in range(0, anova_table.nrow):
      if anova_table[name].isnumeric()[0]:
        assert abs(summaryCol[ind]-anova_table[name][ind,0]) < 1e-6, "expected value: {0}, actual value: {1} and they" \
                                                                     " are different.".format(summaryCol[ind], 
                                                                                              anova_table[name][ind,0])
      else:
        assert summaryCol[ind]==anova_table[name][ind,0], "expected value: {0}, actual value: {1} and they are" \
                                                          " different.".format(summaryCol[ind], anova_table[name][ind,0])
        
if __name__ == "__main__":
  pyunit_utils.standalone_test(test_anova_table_frame)
else:
  test_anova_table_frame()
