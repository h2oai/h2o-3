This file shows all steps need to run xgboost off Hadoop on mr-0xd2.  All AWS keys and stuff have been set up on this machine.  In addition, I put the data in the following place: s3://h2o-public-test-data/xgboost-dist/.  Do a s3cmd ls s3://h2o-public-test-data/xgboost-dist/ to see what directories and files are there.  You will notice that there are airlines and higgs directories containing airlines and higgs dataset in the correct format for xgboost in ingest.  

For XGBoost 0.82, they use Python3.5 or higher.  However, there are several errors concerning bytes-object and string.  Need to replace the following codes:
1. yarn.py, line 48, replace with out = out.decode('utf-8').split('\n')[0].split()
2. yarn.py, line 53, replace classpath = classpath.strip() with classpath = classpath.decode('utf-8').strip()
3. launcher.py, line 55, replace for f in classpath.split(':'): with for f in classpath.decode('utf-8').split(':'):
4. launcher.py, line 25, 


We have several big datasets you can run your tests on:
I. s3://h2o-public-test-data/xgboost-dist/airlines/train/airlinesBillion_7Columns.csv.xgboost
II. s3://h2o-public-test-data/xgboost-dist/higgs/train/HIGGS.xgboost

Please save the following configuration file say as airlines.aws.conf
---------------------------
# General Parameters, see comment for each definition
# choose the booster, can be gbtree or gblinear
booster = gbtree
# choose logistic regression loss function for binary classification
objective = binary:logistic

# Tree Booster Parameters
# step size shrinkage
eta = 0.3
# minimum loss reduction required to make a further partition
gamma = 0.0
alpha = 0
lambda=0.0
subsample=1.0
colsample_bytree=1.0
grow_policy='depthwise'
# minimum sum of instance weight(hessian) needed in a child
min_child_weight = 10
# maximum depth of a tree
seed=123456789
max_depth = 6

# Task Parameters
# the number of round to do boosting
num_round = 50
#num_boost_round = 500
# 0 means do not save any model except the final round model
save_period = 0
# The path of training data
data = "s3://h2o-public-test-data/xgboost-dist/airlines/train"
eval[test]="s3://h2o-public-test-data/xgboost-dist/airlines/train"
model_dir="s3://h2o-public-test-data/xgboost-dist/airlines/model1"
# The path of validation data, used to monitor training process, here [test] sets name of the validation set
# evaluate on training data as well each round
eval_train = 0
---------------------------

Steps to run xgboost off Hadoop off mr-0xd2:
0. Make sure you set your HADOOP_HOME, HADOOP_HDFS_HOME and other paths correctly.  Otherwise, it may complain.  Check your job status and figure out what has not been set.
1. get xgboost as: git clone --recursive https://github.com/dmlc/xgboost.  Or if you want a particular version, goto their releases and download the zip or tar.gz file and unzip them.
2. cd xgboost and perform the following steps:
  a. cd xgboost
  b. cp make/config.mk config.mk
  c. echo "USE_S3=1" >> config.mk
  d. make -j4
3. to submit jobs and run distributed xgboost, type in the following command from any directory:
  a. cd dmlc-core/tracker
  b. Type in the following command:
./dmlc-submit --cluster=yarn --num-workers=4 --worker-memory='50g' /home/wendy/xgboost_reproducibility/xgboost/xgboost /home/wendy/xgboost/airlines.aws.conf > xgboostRunResult

Note that /home/wendy/xgboost_reproducibility/xgboost/xgboost is the full path to the xgboost program kind of like h2odriver.jar.

Note that /home/wendy/xgboost/airlines.aws.conf is the file which tells xgboost where to find the dataset and where to send to finished result too.

Note that the file xgboostRunResult will contains the test-errors printouts.
