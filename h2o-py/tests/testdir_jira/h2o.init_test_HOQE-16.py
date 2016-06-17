from __future__ import print_function
#Currently, our R/python test suite is executed against an established h2o cluster (run.py sets up the cluster). However, we ignore the mode of 
#operation where the h2o cluster is created by the client. Consequently, we may not recognize bugs in h2o.init() for this mode of operation. 
#For this ticket, I think we should create a set of tests that check that h2o.init() is successful for each OS/client interface combination.

#Below is the test that will be implemented:

import h2o

#Call h2o.init() just in case instance is not running
h2o.init(strict_version_check=False)

#First we will shutdown any instance of h2o
h2o.shutdown(prompt = False)

#Load up h2o.init()

h2o.init(strict_version_check=False)

#Get H2OConnection() class
conn = h2o.H2OConnection(ip="localhost", port=54321, start_h2o=True, enable_assertions=True,
						 license=None, nthreads=-1, max_mem_size=None, min_mem_size=None, ice_root=None,
						 strict_version_check=False, proxy=None, https=False, insecure=False, username=None,
						 password=None, cluster_name=None, max_mem_size_GB=None, min_mem_size_GB=None,
						 proxies=None, size=None)


#Get if cluster is up (True) or not (False)
cluster_up = conn.cluster_is_up(conn)

#Hacky way to get if cluster is healthy or not. Might need to fix in cluster_status() function in h2o.py...
conn.json = h2o.H2OConnection.get_json("Cloud?skip_ticks=true")

#Nodes contains healthy status. However, first index in Nodes list is a dictionary. So, we allocate that to a variable and take healthy status
nodes = conn.json['nodes']
nodes = nodes[0]
cluster_health = nodes['healthy']

#Logical test to see if status is healthy or not
if cluster_health == True & cluster_up == True:
	print("Cluster health is up and healthy")
elif cluster_health != True & cluster_up == True:
	raise ValueError('Cluster is up but not healthy')
else:
	raise ValueError('Cluster is not up and is not healthy')

