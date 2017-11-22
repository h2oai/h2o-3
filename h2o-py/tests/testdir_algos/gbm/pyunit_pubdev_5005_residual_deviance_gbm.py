import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

# PUBDEV-5005: GBM residual deviance
# A customer complaint that he is not able to extract the residual deviance after training a GBM model.
# The reason for this is because we use different names for the residual deviance.
#
# 1, For the final model deviance, you can obtained it by calling gbmModel.mean_residual_deviance() and setting
#  the parameters for train, valid and xval.
# 2. If you want to see the history of mean residual deviance, you need to access the training_deviance field of
#  the scoring history
def gbm_residual_deviance():

  cars = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/AirlinesTest.csv.zip"))
  gbm = H2OGradientBoostingEstimator()
  gbm.train(x=list(range(9)), y=9, training_frame=cars, validation_frame=cars)

  # set valid/xval = True if you have provided a validation dataset/enabled cross-validation.
  gbm_mrd = gbm.mean_residual_deviance(train=True,valid=False,xval=False)
  print("Training mean residual deviance is {0}".format(gbm_mrd))

  # if you want to see the scoring history of validation, look at field training_deviance of scoring history
  mean_residual_deviance_history = extract_scoring_history_field(gbm, "training_deviance")
  print("History of training mean residual deviance during training is {0}".format(mean_residual_deviance_history))

  assert abs(mean_residual_deviance_history[-1]-gbm_mrd) < 1e-12, "mean_residual_deviance function is not working."

def extract_scoring_history_field(aModel, fieldOfInterest):
  """
  Given a fieldOfInterest that are found in the model scoring history, this function will extract the list
  of field values for you from the model.

  :param aModel: H2O model where you want to extract a list of fields from the scoring history
  :param fieldOfInterest: string representing a field of interest.
  :return: List of field values or None if it cannot be found
  """

  allFields = aModel._model_json["output"]["scoring_history"]._col_header
  if fieldOfInterest in allFields:
    cellValues = []
    fieldIndex = allFields.index(fieldOfInterest)
    for eachCell in aModel._model_json["output"]["scoring_history"].cell_values:
      cellValues.append(eachCell[fieldIndex])
    return cellValues
  else:
    return None


if __name__ == "__main__":
  pyunit_utils.standalone_test(gbm_residual_deviance)
else:
  gbm_residual_deviance()
