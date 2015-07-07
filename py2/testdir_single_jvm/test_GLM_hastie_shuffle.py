import unittest, time, sys, random, copy
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_cmd, h2o_import as h2i, h2o_jobs, h2o_glm, h2o_util
from h2o_test import verboseprint, dump_json, OutputObj

# Dataset created from this:
# Elements of Statistical Learning 2nd Ed.; Hastie, Tibshirani, Friedman; Feb 2011
# example 10.2 page 357
# Ten features, standard independent Gaussian. Target y is:
#   y[i] = 1 if sum(X[i]) > .34 else -1
# 9.34 is the median of a chi-squared random variable with 10 degrees of freedom 
# (sum of squares of 10 standard Gaussians)
# http://www.stanford.edu/~hastie/local.ftp/Springer/ESLII_print5.pdf

# from sklearn.datasets import make_hastie_10_2
# import numpy as np
# i = 1000000
# f = 10
# (X,y) = make_hastie_10_2(n_samples=i,random_state=None)
# y.shape = (i,1)
# Y = np.hstack((X,y))
# np.savetxt('./1mx' + str(f) + '_hastie_10_2.data', Y, delimiter=',', fmt='%.2f');

def glm_doit(self, csvFilename, bucket, csvPathname, timeoutSecs=30):
    print "\nStarting GLM of", csvFilename
    # we can force a col type to enum now? with param columnTypes
    # "Numeric"
    # make the last column enum
    # Instead of string for parse, make this a dictionary, with column index, value
    # that's used for updating the ColumnTypes array before making it a string for parse
    columnTypeDict = {10: 'Enum'}
    parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, columnTypeDict=columnTypeDict,
        hex_key=csvFilename + ".hex", schema='put', timeoutSecs=30)
    pA = h2o_cmd.ParseObj(parseResult)
    iA = h2o_cmd.InspectObj(pA.parse_key)
    parse_key = pA.parse_key
    numRows = iA.numRows
    numCols = iA.numCols
    labelList = iA.labelList
    for i in range(10):
        print "Summary on column", i
        # FIX! how come only 0 works here for column
        co = h2o_cmd.runSummary(key=parse_key, column=i)
        for k,v in co:
            print k, v

    expected = []
    allowedDelta = 0

    labelListUsed = list(labelList)
    labelListUsed.remove('C11')
    numColsUsed = numCols - 1

    parameters = {
        'validation_frame': parse_key,
        'ignored_columns': None,
        # FIX! for now just use a column that's binomial
        'response_column': 'C11',
        # FIX! when is this needed? redundant for binomial?
        'balance_classes': False,
        'max_after_balance_size': None,
        'standardize': False,
        'family': 'binomial', 
        'link': None, 
        'alpha': '[1e-4]',
        'lambda': '[0.5,0.25, 0.1]',
        'lambda_search': None,
        'nlambdas': None,
        'lambda_min_ratio': None,
        # 'use_all_factor_levels': False,
    }


    start = time.time()
    model_key = 'hastie_glm.hex'
    bmResult = h2o.n0.build_model(
        algo='glm',
        model_id=model_key,
        training_frame=parse_key,
        parameters=parameters,
        timeoutSecs=60)
    bm = OutputObj(bmResult, 'bm')

    modelResult = h2o.n0.models(key=model_key)
    model = OutputObj(modelResult['models'][0]['output'], 'model')

    h2o_glm.simpleCheckGLM(self, model, parameters, labelList, labelListUsed)

    cmmResult = h2o.n0.compute_model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
    cmm = OutputObj(cmmResult, 'cmm')

    mmResult = h2o.n0.model_metrics(model=model_key, frame=parse_key, timeoutSecs=60)
    mm = OutputObj(mmResult, 'mm')

    prResult = h2o.n0.predict(model=model_key, frame=parse_key, timeoutSecs=60)
    pr = OutputObj(prResult['model_metrics'][0]['predictions'], 'pr')

    # compare this glm to the first one. since the files are replications, the results
    # should be similar?
    if self.validation1:
        h2o_glm.compareToFirstGlm(self, 'AUC', validation, self.validation1)
    else:
        # self.validation1 = copy.deepcopy(validation)
        self.validation1 = None


class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(1)
        global SYNDATASETS_DIR
        SYNDATASETS_DIR = h2o.make_syn_dir()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    validation1 = {}
    def test_GLM_hastie_shuffle(self):
        # gunzip it and cat it to create 2x and 4x replications in SYNDATASETS_DIR
        # FIX! eventually we'll compare the 1x, 2x and 4x results like we do
        # in other tests. (catdata?)

        # This test also adds file shuffling, to see that row order doesn't matter
        csvFilename = "1mx10_hastie_10_2.data.gz"
        bucket = 'home-0xdiag-datasets'
        csvPathname = 'standard' + '/' + csvFilename
        fullPathname = h2i.find_folder_and_filename(bucket, csvPathname, returnFullPath=True)

        glm_doit(self, csvFilename, bucket, csvPathname, timeoutSecs=30)

        filename1x = "hastie_1x.data"
        pathname1x = SYNDATASETS_DIR + '/' + filename1x
        h2o_util.file_gunzip(fullPathname, pathname1x)

        filename1xShuf = "hastie_1x.data_shuf"
        pathname1xShuf = SYNDATASETS_DIR + '/' + filename1xShuf
        h2o_util.file_shuffle(pathname1x, pathname1xShuf)

        filename2x = "hastie_2x.data"
        pathname2x = SYNDATASETS_DIR + '/' + filename2x
        h2o_util.file_cat(pathname1xShuf, pathname1xShuf, pathname2x)

        filename2xShuf = "hastie_2x.data_shuf"
        pathname2xShuf = SYNDATASETS_DIR + '/' + filename2xShuf
        h2o_util.file_shuffle(pathname2x, pathname2xShuf)
        glm_doit(self, filename2xShuf, None, pathname2xShuf, timeoutSecs=45)

        # too big to shuffle?
        filename4x = "hastie_4x.data"
        pathname4x = SYNDATASETS_DIR + '/' + filename4x
        h2o_util.file_cat(pathname2xShuf,pathname2xShuf,pathname4x)
        glm_doit(self,filename4x, None, pathname4x, timeoutSecs=120)

if __name__ == '__main__':
    h2o.unit_main()
