#!/usr/bin/env python
# -*- encoding: utf-8 -*-
# This script must run from its own directory
import gen_csharp
import gen_docs_json
import gen_java
import gen_python
import gen_R
import gen_thrift
import bindings
import sys
import os
sys.path.insert(0, "../../scripts")
import run
import atexit

bindings.check_requirements()

# Create results folder, where H2OCloud stores its logs
results_dir = "../build/logs"
if not os.path.exists(results_dir):
    os.makedirs(results_dir)

# Allow override of the H2O jarfile so we can use this with projects which extend h2o.jar
h2o_jarfile = os.getenv('H2O_JARFILE', '../../build/h2o.jar')
h2o_java_version = os.getenv('H2O_JAVA_VERSION', '1.8')
h2o_jvm_cp = os.getenv('H2O_BINDINGS_EXTRA_CLASSPATH', '')
h2o_jvm_opts = ["-Dsys.ai.h2o.ext.rest.toggle.XGBoost=true", "-Dsys.ai.h2o.ext.core.toggle.XGBoost=true", 
                "-Dsys.ai.h2o.ext.core.toggle.KrbStandalone=false"]
if h2o_java_version != '1.8':
    h2o_jvm_opts += ["--add-opens=java.base/java.lang=ALL-UNNAMED"]

# Start H2O cloud
print("Starting H2O cloud...")
cloud = run.H2OCloud(
    cloud_num=0,
    use_client=False,
    use_external_xgboost=False,
    nodes_per_cloud=1,
    h2o_jar=os.path.abspath(h2o_jarfile),
    base_port=48000,
    xmx="4g",
    cp=h2o_jvm_cp,
    jvm_opts=h2o_jvm_opts,
    output_dir=results_dir,
    test_ssl=False,
    login_config=None,
    strict_port=False
)
atexit.register(lambda: cloud.stop())

cloud.start()
cloud.wait_for_cloud_to_be_up()

# Manipulate the command line arguments, so that bindings module would know which cloud to use
sys.argv.insert(1, "--usecloud")
sys.argv.insert(2, "%s:%s" % (cloud.get_ip(), cloud.get_port()))

# Actually generate all the bindings
print()
gen_R.main()
gen_python.main()
gen_docs_json.main()
gen_thrift.main()
gen_csharp.main()
gen_java.main()
bindings.done()
print()
