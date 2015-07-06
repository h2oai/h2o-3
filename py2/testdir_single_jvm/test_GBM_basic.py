import unittest, sys, time 
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i
from h2o_test import dump_json, verboseprint, OutputObj
from tabulate import tabulate

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init(1, java_heap_GB=12)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_GBM_basic(self):
        bucket = 'home-0xdiag-datasets'
        importFolderPath = 'standard'
        trainFilename = 'covtype.shuffled.90pct.data'
        train_key = 'covtype.train.hex'
        model_key = 'GBMModelKey'
        timeoutSecs = 1800
        csvPathname = importFolderPath + "/" + trainFilename

        # FIX! do I need to force enum for classification? what if I do regression after this?
        columnTypeDict = {54: 'Enum'}
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, columnTypeDict=columnTypeDict,
            schema='local', chunk_size=4194304, hex_key=train_key, timeoutSecs=timeoutSecs)

        pA = h2o_cmd.ParseObj(parseResult)
        iA = h2o_cmd.InspectObj(pA.parse_key)
        parse_key = pA.parse_key
        numRows = iA.numRows
        numCols = iA.numCols
        labelList = iA.labelList

        labelListUsed = list(labelList)
        numColsUsed = numCols

        # run through a couple of parameter sets
        parameters = []
        parameters.append({
            'response_column': 'C55',
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
        })

        parameters.append({
            'response_column': 'C55', 
            'loss': 'multinomial',
            # This does nothing! intent is solely based on type of response col
            'ntrees': 1, 
            'max_depth': 20, 
            'min_rows': 3, 
            'nbins': 40, 
            'learn_rate': 0.2, 
            })

        model_key = 'covtype_gbm.hex'

        for p in parameters:
            bmResult = h2o.n0.build_model(
                algo='gbm',
                model_id=model_key,
                training_frame=train_key,
                validation_frame=train_key,
                parameters=p,
                timeoutSecs=60)
            bm = OutputObj(bmResult, 'bm')

            modelResult = h2o.n0.models(key=model_key)
            model = OutputObj(modelResult['models'][0]['output'], 'model')

            cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
            # cmm = OutputObj(cmmResult, 'cmm')
            # print "\nLook!, can use dot notation: cmm.cm.confusion_matrix", cmm.cm.confusion_matrix, "\n"

            vis = OutputObj(model.variable_importances, 'vis')

            # just the first 10
            visDataChopped = [v[0:9] for v in vis.data]
            names = visDataChopped[0]
            relativeImportance = visDataChopped[1]
            print "names:", names
            print "relativeImportance:", relativeImportance
            scaledImportance = visDataChopped[2]
            percentage = visDataChopped[3]
            print "\nvis\n", tabulate(visDataChopped[1:], headers=names)
            # print "\nrelativeImportance (10)\n", tabulate(relativeImportance, headers=names)
            # print "\nscaledImportance (10)\n", tabulate(scaledImportance, headers=names)
            # print "\npercentage (10)\n", tabulate(percentage, headers=names)

            print "will say Regression or Classification. no Multinomial?"
            print "model.model_category", model.model_category
            assert model.model_category=='Multinomial', model.model_category

            print "FIX! why is mse 0 and mse_train Nan?"
            print "model:", dump_json(model)
            print "model.training_metrics.MSE:", model.training_metrics.MSE
            print "model.training_metrics.logloss:", model.training_metrics.logloss

            if 1==0:
                print ""
                for i,c in enumerate(cmm.cm):
                    print "\ncmms.cm[%s]" % i, tabulate(c)
                print ""



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
