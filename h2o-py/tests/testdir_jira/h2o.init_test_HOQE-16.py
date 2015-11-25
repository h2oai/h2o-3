#Currently, our R/python test suite is executed against an established h2o cluster (run.py sets up the cluster). However, we ignore the mode of 
#operation where the h2o cluster is created by the client. Consequently, we may not recognize bugs in h2o.init() for this mode of operation. 
#For this ticket, I think we should create a set of tests that check that h2o.init() is successful for each OS/client interface combination.

#Below is the test that will be implemented:

#Import h2o and load up h2o.init()
import h2o as h
h.init()

#Get H2OConnection() class
h2o = h.H2OConnection()

#Get if cluster is up (True) or not (False)
cluster_up = h2o.cluster_is_up(h2o)

#Hacky way to get if cluster is healthy or not. Might need to fix in cluster_status() function in h2o.py...
h2o.json = h.H2OConnection.get_json("Cloud?skip_ticks=true")

#Nodes contains healthy status. However, first index in Nodes list is a dictionary. So, we allocate that to a variable and take healthy status
nodes = h2o.json['nodes']
nodes = nodes[0]
cluster_health = nodes['healthy']

#Logical test to see if status is healthy or not
if cluster_health == True & cluster_up == True:
	print "Cluster health is up and healthy"
elif cluster_health != True & cluster_up == True:
	raise ValueError('Cluster is up but not healthy')
else:
	raise ValueError('Cluster is not up and is not healthy')

