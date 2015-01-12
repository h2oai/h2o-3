"""
This module implements the communication REST layer for the python <-> H2O connection.
"""

import requests
import urllib
import time

##############################################################################
#
# Cluster connection
#
H2OCONN = None  # Default connection
class H2OConnection(object):
  def __init__(self,ip="localhost",port=54321):
    assert isinstance(port,int) and 0 <= port <= 65535
    self._ip = ip
    self._port = port
    cld = self.connect()
    ncpus=0;  mmax=0
    for n in cld['nodes']:
      ncpus += n['num_cpus']
      mmax  += n['max_mem']
    print "Connected to cloud '"+cld['cloud_name']+"' size",cld['cloud_size'],"ncpus",ncpus,"maxmem",_get_human_readable_size(mmax)
    global H2OCONN
    H2OCONN = self              # Default connection is last openned

  # Dumb url prefix
  def url(self):  return "http://"+self._ip+":"+str(self._port)+"/"

  # Does not actually "connect", instead simply tests that the cluster can be
  # reached, is of a certain size, and is taking basic status commands
  def connect(self,size=1):
    while True:
      cld = self._doSafeGet(self.buildURL("Cloud",{}))
      if not cld['cloud_healthy']:
        raise ValueError("Cluster reports unhealthy status",cld)
      if cld['cloud_size'] >= size and cld['consensus']: return cld
      # Cloud too small or voting in progress; sleep; try again
      time.sleep(0.1)

  # Import a single file; very basic error checking
  # Returns h2o Key
  def ImportFile(self,path):
    j = self._doSafeGet(self.buildURL("ImportFiles",{'path':path}))
    if j['fails']:  raise ValueError("ImportFiles of "+path+" failed on "+j['fails'])
    return j['keys'][0]

  # Return basic parse setup object
  def ParseSetup(self,rawkey):
    # Unable to use 'requests.params=' syntax because it flattens array
    # parameters, but ParseSetup really expects a real array of Keys.
    j = self._doSafeGet(self.buildURL("ParseSetup",{'srcs':[rawkey]}))
    if not j['isValid']: raise ValueError("ParseSetup not Valid",j)
    return j

  # Trigger a parse; blocking; removeFrame just keep the Vec keys
  def Parse(self,setup,hexname):
    # Some initial parameters
    p = {'delete_on_done':True,'blocking':True,'removeFrame':True,'hex':hexname}
    # Copy selected keys
    for key in ['ncols','sep','columnNames','pType','checkHeader','singleQuotes']:
      p[key] = setup[key]
    # Extract only 'name' from each src in the array of srcs
    p['srcs'] = [src['name'] for src in setup['srcs']]
    # Request blocking parse
    # TODO: POST vs GET
    j = self._doSafeGet(self.buildURL("Parse",p))
    if j['job']['status'] != 'DONE': raise ValueError("Parse status expected to be DONE, instead is "+j['job']['status'])
    if j['job']['progress'] != 1.0: raise ValueError("Parse progress expected to be 1.0, instead is "+j['job']['progress'])
    return j

  # Remove a Key (probably just a Vec)
  def Remove(self,key):
    return self._doSafeGet(self.buildURL("Remove",{"key":key}))

  # Fire off a Rapids expression
  def Rapids(self,expr):
    return self._doSafeGet(self.buildURL("Rapids",{"ast":urllib.quote(expr)}))

  def Frame(self,key):
    return self._doSafeGet(self.buildURL("3/Frames/"+str(key),{}))

  def GBM(self,distribution,shrinkage,ntrees,interaction_depth,x,train_frame,test_frame=None):
    p = {'loss':distribution,'learn_rate':shrinkage,'ntrees':ntrees,'max_depth':interaction_depth,'variable_importance':False,'response_column':x,'training_frame':train_frame}
    if test_frame: p['validation_frame'] = test_frame
    j = self._doJob(self._doSafeGet(self.buildURL("GBM",p)))
    j = self._doSafeGet(self.buildURL("3/Models/"+j['dest']['name'],{}))
    return j['models'][0]

  def DeepLearning(self,x,train_frame,test_frame=None,**kwargs):
    kwargs['response_column'] = x
    kwargs['training_frame'] = train_frame
    if test_frame: kwargs['validation_frame'] = test_frame
    j = self._doJob(self._doSafeGet(self.buildURL("DeepLearning",kwargs)))
    j = self._doSafeGet(self.buildURL("3/Models/"+j['dest']['name'],{}))
    return j['models'][0]

  def Job(self,jobkey):
    return self._doSafeGet(self.buildURL("Jobs/"+jobkey,{}))

  # Block until a job is done
  def _doJob(self,j):
    if 'validation_error_count' in j: raise ValueError("Argument errors:"+str(j['validation_messages']))
    print                       # Blank line for progress bar
    job = j['jobs'][0]
    jobkey = job['key']['name']
    sleep = 0.1
    while job['status']=="RUNNING":
      _update_progress(job['progress'])
      time.sleep(sleep)
      if sleep < 1.0: sleep += 0.1
      j = self.Job(jobkey)
      job = j['jobs'][0]
    _update_progress(job['progress'])
    return job
  

  # "Safe" REST calls.  Check for errors in a common way
  def _doSafeGet(self,url):
    r = requests.get(url)
    # Missing a non-json response check, e.g. 404 check here
    j = r.json()
    if 'errmsg' in j: raise ValueError(j['errmsg'])
    if 'http_status' in j:
      if j['http_status']==404 or j['http_status']==500: raise ValueError(j['msg'])
    return j

  # function to build a URL from a base and a dictionary of params.  'request'
  # has such a thing but it flattens lists and we need the actual list
  # complete with '[]'
  def buildURL(self,base,params):
    s = self.url()+base+".json"
    sep = '?'
    for k,v in params.items():
      s += sep + k + "="
      if isinstance(v,list):
        sep2 = '['
        for l in v:
          s += sep2 + str(l).encode('utf-8')
          sep2 = ','
        s += ']'
      else:
        s += str(v).encode('utf-8')
      sep = '&'
    return s
    

##############################################################################

# Dump out a progress bar
def _update_progress(progress):
  print '\r[{0}] {1}%'.format('#'*int(progress*100), progress*100)

# Simple stackoverflow pretty-printer for big numbers
def _get_human_readable_size(num):
  exp_str = [ (0, 'B'), (10, 'KB'),(20, 'MB'),(30, 'GB'),(40, 'TB'), (50, 'PB'),]               
  i = 0
  while i+1 < len(exp_str) and num >= (2 ** exp_str[i+1][0]):
    i += 1
    rounded_val = round(float(num) / 2 ** exp_str[i][0], 2)
  return '%s %s' % (rounded_val, exp_str[i][1])



# Global list of pending expressions and deletes to ship to the cluster
_CMD = None
_TMPS= None