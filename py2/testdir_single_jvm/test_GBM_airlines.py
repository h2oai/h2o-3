import unittest, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i
from h2o_test import dump_json, verboseprint, OutputObj

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init(1, use_hdfs=True, hdfs_version='cdh4', hdfs_name_node='172.16.2.176', java_heap_GB=14)


    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_GBM_airlines(self):
        files = [
                 ('datasets', 'airlines_all.05p.csv', 'airlines_all.05p.hex', 1800, 'IsDepDelayed'),
                 # ('datasets', 'airlines_all.csv', 'airlines_all.hex', 1800, 'IsDepDelayed')
                ]

        for importFolderPath, csvFilename, trainKey, timeoutSecs, response in files:
            # PARSE train****************************************
            csvPathname = importFolderPath + "/" + csvFilename

            model_key = 'GBMModelKey'
            # IsDepDelayed might already be enum, but just to be sure
            parseResult = h2i.import_parse(path=csvPathname, schema='hdfs', hex_key=trainKey, 
                columnTypeDict={'IsDepDelayed': 'Enum'}, timeoutSecs=timeoutSecs)

            pA = h2o_cmd.ParseObj(parseResult)
            iA = h2o_cmd.InspectObj(pA.parse_key)
            parse_key = pA.parse_key
            numRows = iA.numRows
            numCols = iA.numCols
            labelList = iA.labelList

            labelListUsed = list(labelList)
            numColsUsed = numCols

            parameters = {
                'validation_frame': trainKey,
                # 'ignored_columns': '[CRSDepTime,CRSArrTime,ActualElapsedTime,CRSElapsedTime,AirTime,ArrDelay,DepDelay,TaxiIn,TaxiOut,Cancelled,CancellationCode,Diverted,CarrierDelay,WeatherDelay,NASDelay,SecurityDelay,LateAircraftDelay,IsArrDelayed]',
                'response_column': response,
                # 'balance_classes':
                # 'max_after_balance_size':
                'ntrees': 2,
                'max_depth': 10,
                'min_rows': 3,
                'nbins': 40,
                'learn_rate': 0.2,
                # 'loss': 'multinomial',
                # FIX! doesn't like it?
                # 'loss': 'Bernoulli',
                # FIX..no variable importance for GBM yet?
                # 'variable_importance': False,
                # 'seed': 
            }

            bmResult = h2o.n0.build_model(
                algo='gbm',
                model_id=model_key,
                training_frame=parse_key,
                parameters=parameters,
                timeoutSecs=360)
            bm = OutputObj(bmResult, 'bm')

            modelResult = h2o.n0.models(key=model_key)
            model = OutputObj(modelResult['models'][0]['output'], 'model')

            cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            cmm = OutputObj(cmmResult, 'cmm')
            # print "\nLook!, can use dot notation: cmm.cm.confusion_matrix", cmm.cm.confusion_matrix, "\n"

            mmResult = h2o.n0.model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            mmResultShort = mmResult['model_metrics'][0]
            del mmResultShort['frame'] # too much!
            mm = OutputObj(mmResultShort, 'mm')

            prResult = h2o.n0.predict(model=model_key, frame=parse_key, timeoutSecs=60)
            pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')

            # too slow!
            # h2o_cmd.runStoreView()

if __name__ == '__main__':
    h2o.unit_main()
