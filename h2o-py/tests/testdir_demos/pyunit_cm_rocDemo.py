#----------------------------------------------------------------------
# Purpose:  Split Airlines dataset into train and validation sets.
#           Build model and predict on a test Set.
#           Print Confusion matrix and performance measures for test set
#----------------------------------------------------------------------

import sys
sys.path.insert(1, "../../")
import h2o
#import pylab as pl

def demo_cm_roc(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)

    #uploading data file to h2o
    air = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip"))

    #Constructing validation and train sets by sampling (20/80)
    #creating a column as tall as air.nrow()

    r = air[0].runif()
    air_train = air[r < 0.8]
    air_valid = air[r >= 0.8]

    myX = ["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek"]
    myY = "IsDepDelayed"

    #gbm
    gbm = h2o.gbm(x=air_train[myX], y=air_train[myY], validation_x= air_valid[myX],
                  validation_y=air_valid[myY], loss="bernoulli", ntrees=100, max_depth=3, learn_rate=0.01)
    gbm.show()
    gbm._model_json['output']['variable_importances'].show()

    #glm
    glm = h2o.glm(x=air_train[myX], y=air_train[myY], validation_x= air_valid[myX],
                  validation_y=air_valid[myY], family = "binomial", solver="L_BFGS")
    glm.show()
    glm._model_json['output']['coefficients_magnitude'].show()


    #uploading test file to h2o
    air_test = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTest.csv.zip"))

    #predicting & performance on test file
    gbm_pred = gbm.predict(air_test)
    gbm_pred.head()

    gbm_perf = gbm.model_performance(air_test)
    gbm_perf.show()

    glm_pred = glm.predict(air_test)
    glm_pred.head()

    glm_perf = glm.model_performance(air_test)
    glm_perf.show()

    #Building confusion matrix for test set
    gbm_CM = gbm_perf.confusion_matrices()
    print(gbm_CM)
    glm_CM = glm_perf.confusion_matrices()
    print(glm_CM)

    #Plot ROC for test set
    print('GBM Precision: {0}'.format(gbm_perf.precision()))
    print('GBM Accuracy: {0}'.format(gbm_perf.accuracy()))
    print('GBM AUC: {0}'.format(gbm_perf.auc()))
    #gbm_tpr = [rc[1] if rc[1] != '' else 0.0 for rc in gbm_perf.recall()]
    #gbm_fpr = [1.0 - sp[1] if sp[1] != '' else 1.0 for sp in gbm_perf.specificity()]
    #pl.clf()
    #pl.plot(gbm_fpr, gbm_tpr, label='GBM ROC Curve (AUC = %0.2f)' % gbm_perf.auc())
    #pl.plot([0, 1], [0, 1], 'k--')
    #pl.xlim([0.0, 1.0])
    #pl.ylim([0.0, 1.0])
    #pl.xlabel('False Positive Rate')
    #pl.ylabel('True Positive Rate')
    #pl.title('GBM ROC curve')
    #pl.legend(loc="lower right")
    #pl.savefig('gbm_roc.png')

    print('GLM Precision: {0}'.format(glm_perf.precision()))
    print('GLM Accuracy: {0}'.format(glm_perf.accuracy()))
    print('GLM AUC: {0}'.format(glm_perf.auc()))
    #glm_tpr = [rc[1] if rc[1] != '' else 0.0 for rc in glm_perf.recall()]
    #glm_fpr = [1.0 - sp[1] if sp[1] != '' else 1.0 for sp in glm_perf.specificity()]
    #pl.clf()
    #pl.plot(glm_fpr, glm_tpr, label='GLM ROC Curve (AUC = %0.2f)' % glm_perf.auc())
    #pl.plot([0, 1], [0, 1], 'k--')
    #pl.xlim([0.0, 1.0])
    #pl.ylim([0.0, 1.0])
    #pl.xlabel('False Positive Rate')
    #pl.ylabel('True Positive Rate')
    #pl.title('GLM ROC curve')
    #pl.legend(loc="lower right")
    #pl.savefig('glm_roc.png')

if __name__ == "__main__":
  h2o.run_test(sys.argv, demo_cm_roc)
