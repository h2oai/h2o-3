# -*- encoding: utf-8 -*-
"""
  Currently, our R/Python test suite is executed against an established h2o cluster (run.py sets up the cluster).
  However, we ignore the mode of operation where the h2o cluster is created by the client. Consequently, we may not
  recognize bugs in h2o.init() for this mode of operation.
  For this ticket, I think we should create a set of tests that check that h2o.init() is successful for each
  OS/client interface combination.

  Below is the test that will be implemented:
"""
from __future__ import print_function
import sys
sys.path.insert(0, "../..")
import h2o

PORT = 55330

# Check whether there is already an instance running at the specified port, and if so shut it down.
try:
    conn = h2o.connect(port=PORT)
    conn.shutdown(prompt=False)
except h2o.H2OConnectionError:
    pass


# Now start a new H2O server and connect to it.
server = h2o.start(port=str(PORT) + "+")
conn = h2o.connect(server=server)

# Get if cluster is up (True) or not (False)
cluster_up = conn.cluster_is_up()

# Check if cluster is healthy
cluster_healthy = all(node["healthy"] for node in conn.info().nodes)

# Logical test to see if status is healthy or not
if cluster_healthy and cluster_up:
    print("Cluster health is up and healthy")
elif not cluster_healthy and cluster_up:
    raise ValueError("Cluster is up but not healthy")
else:
    raise ValueError("Cluster is not up and is not healthy")
