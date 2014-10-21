import os

class CloudConfig():
    basedir = os.path.abspath("../..")
    cloudType = 'local_cloud'
    topology = {
        "local_cloud" : {
            "ip" : '127.0.0.1',
            "port" : 54321,
            "timeoutSecs" : 240
        },
        "cloudByFlatFile" : {
            "flatFile" : "flatfile.txt",
            "timeoutSecs" : 240
        }
    }
    data = {
        "prostate" : ("file", os.path.join(basedir, "smalldata/logreg/prostate.csv")),
        "airlines" : ("file", os.path.join(basedir, "smalldata/airlines/allyears2k_headers.zip"))
    }
    def cloud_type(self): return self.cloudType
    def cloud_topology(self): return self.topology
