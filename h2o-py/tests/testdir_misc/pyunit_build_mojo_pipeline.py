#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys
sys.path.insert(1,"../../")
import h2o
import os
import subprocess
from subprocess import STDOUT, PIPE
from tests import pyunit_utils
from h2o.estimators.kmeans import H2OKMeansEstimator
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator


def build_mojo_pipeline():
    results_dir = pyunit_utils.locate("results")
    iris_csv = pyunit_utils.locate('smalldata/iris/iris_train.csv')
    iris = h2o.import_file(iris_csv)

    pca = H2OPrincipalComponentAnalysisEstimator(k=2)
    pca.train(training_frame=iris)

    principal_components = pca.predict(iris)

    km = H2OKMeansEstimator(k=3)
    km.train(training_frame=principal_components)

    pca_mojo_path = pca.download_mojo(path=results_dir)
    km_mojo_path = km.download_mojo(get_genmodel_jar=True, path=results_dir)

    java_cmd = ["java", "-cp", os.path.join(results_dir, "h2o-genmodel.jar"), "hex.genmodel.tools.BuildPipeline", "--mapping"]
    pca_mojo_name = os.path.basename(pca_mojo_path).split('.')[0]
    for i, pc in enumerate(principal_components.columns):
        mapping = pc + '=' + pca_mojo_name + ':' + str(i)
        java_cmd += [mapping]
    java_cmd += ["--output", os.path.join(results_dir, "pipe.zip"), "--input", km_mojo_path, pca_mojo_path]

    subprocess.Popen(java_cmd, stdout=PIPE, stderr=STDOUT).communicate()

    h2o_preds = km.predict(principal_components)
    mojo_preds_raw = h2o.mojo_predict_csv(
        input_csv_path=iris_csv,
        mojo_zip_path=os.path.join(results_dir, "pipe.zip")
    )
    mojo_preds = h2o.H2OFrame([c['cluster'] for c in mojo_preds_raw], column_names=['predict'])
    
    assert (mojo_preds == h2o_preds).mean()[0, "predict"] == 1
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(build_mojo_pipeline)
else:
    build_mojo_pipeline()
