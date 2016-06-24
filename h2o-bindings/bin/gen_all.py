#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function
import gen_csharp
import gen_docs_json
import gen_java
import gen_python
import gen_thrift
import bindings
import sys, os
sys.path.insert(0, "../../scripts")
import run

# Start H2O cloud
print("Starting H2O cloud...")
cloud = run.H2OCloud(
	cloud_num=0,
	use_client=False,
	nodes_per_cloud=1,
	h2o_jar=os.path.abspath("../../build/h2o.jar"),
	base_port=48000,
	xmx="4g",
	cp="",
	output_dir="results"
)
cloud.start()
cloud.wait_for_cloud_to_be_up()

sys.argv.insert(1, "--usecloud")
sys.argv.insert(2, "%s:%s" % (cloud.get_ip(), cloud.get_port()))

print()
gen_java.main()
gen_python.main()
gen_docs_json.main()
gen_thrift.main()
gen_csharp.main()
bindings.done()
print()

cloud.stop()
