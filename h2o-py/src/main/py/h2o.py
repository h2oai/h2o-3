import csv, requests, sys, urllib, uuid, traceback, time
from tabulate import tabulate
from math import sqrt,isnan

###############################################################################
# Frame represents a 2-D array of data, uniform in each column.  The data may
# be local, or may be in an H2O cluster.  The data is loaded from a CSV file,
# and is either a python-process-local file or cluster-local file, or a list of
# Vecs (columns of data).
class H2OFrame(object):
  def __init__(self, local_fname=None, remote_fname=None, vecs=None):
    # Read a CSV file
    if remote_fname:             # Read remotely into cluster
      if not H2OCONN: raise ValueError("No open h2o connection")
      rawkey = H2OCONN.ImportFile(remote_fname)
      setup  = H2OCONN.ParseSetup(rawkey)
      parse  = H2OCONN.Parse(setup,_py_tmp_key())
      cols   = parse['columnNames']
      rows   = parse['rows']
      veckeys= parse['vecKeys']
      self._vecs = [Vec(str(col),Expr(op=veckey['name'],length=rows))  for idx,(col,veckey) in enumerate(zip(cols,veckeys))]
      print "Imported",remote_fname,"into cluster with",rows,"rows and",len(cols),"cols"
    elif local_fname:            # Read locally into python process
      with open(local_fname, 'rb') as csvfile:
        self._vecs = []
        for name in csvfile.readline().split(','): 
          self._vecs.append(Vec(name.rstrip(), Expr([])))
        for row in csv.reader(csvfile):
          for i,data in enumerate(row):
            self._vecs[i].append(data)
      print "Imported",local_fname,"into local python process"
    # Construct from an array of Vecs already passed in
    elif vecs is not None:
      vlen = vecs[0].__len__()
      for v in vecs:  
        if not isinstance(v,Vec): raise ValueError("Not a list of Vecs")
        if v.__len__() != vlen : raise ValueError("Vecs not the same size, "+str(vlen)+" vs "+str(len(v)))
      self._vecs = vecs
    else: raise ValueError("Frame made from CSV file or an array of Vecs only")

  # Print [col, cols...]
  def show(self): 
    s = ""
    for vec in self._vecs: s += vec.show()
    return s
  # Comment out to help in debugging
  #def __str__(self): return self.show()

  # In-depth description of data frame
  def describe(self):
    self._vecs[0].summary()
    print "Rows:",len(self._vecs[0]),"Cols:",len(self)
    headers = [vec._name for vec in self._vecs]
    table = [ 
      _row('type'   ,self._vecs,None),
      _row('mins'   ,self._vecs,0),
      _row('mean'   ,self._vecs,None),
      _row('maxs'   ,self._vecs,0),
      _row('sigma'  ,self._vecs,None),
      _row('zeros'  ,self._vecs,None),
      _row('missing',self._vecs,None)
    ]
    print tabulate(table,headers)

  # Column selection via integer, string (name) returns a Vec
  # Column selection via slice returns a subset Frame
  def __getitem__(self,i):
    if isinstance(i,int): return self._vecs[i]
    if isinstance(i,str):
      for v in self._vecs:  
        if i==v._name: return v
      raise ValueError("Name "+i+" not in Frame")
    # Slice; return a Frame not a Vec
    if isinstance(i,slice): return H2OFrame(vecs=self._vecs[i])
    # Row selection from a boolean Vec
    if isinstance(i,Vec):
      if len(i) != len(self._vecs[0]):
        raise ValueError("Vec len()="+len(self._vecs[0])+" cannot be broadcast across len(i)="+len(i))
      return H2OFrame(vecs=[x.row_select(i) for x in self._vecs])
    raise NotImplementedError

  # Set a column
  def __setitem__(self,b,c):
    if isinstance(b,str):
      for i,v in enumerate(self._vecs):
        if b==v._name: break
      else: raise ValueError("Name "+i+" not in Frame")
      if len(c) != len(v):
        raise ValueError("Vec len()="+len(c)+" not compatible with Frame len()="+len(v))
      c._name = b
      self._vecs[i] = c
      return self
    raise NotImplementedError
    

  # Column selection via integer, string (name) returns a Vec
  # Column selection via slice returns a subset Frame
  def __delitem__(self,i):
    if isinstance(i,str):
      for v in self._vecs:  
        if i==v._name:
          self._vecs.remove(v)
          return self
      raise KeyError("Name "+i+" not in Frame")
    raise NotImplementedError
    

  # Number of columns
  def __len__(self): return len(self._vecs)

  # Addition
  def __add__(self,i):
    if len(self)==0: return self
    if isinstance(i,H2OFrame):
      if len(i) != len(self):
        raise ValueError("Frame len()="+len(self)+" cannot be broadcast across len(i)="+len(i))
      return H2OFrame(vecs=[x+y for x,y in zip(self._vecs,i._vecs)])
    if isinstance(i,Vec):
      if len(i) != len(self._vecs[0]):
        raise ValueError("Vec len()="+len(self._vecs[0])+" cannot be broadcast across len(i)="+len(i))
      return H2OFrame(vecs=[x+i for x in self._vecs])
    if isinstance(i,int):
      return H2OFrame(vecs=[x+i for x in self._vecs])
    raise NotImplementedError

  def __radd__(self,i): return self+i  # Add is associative

def _row(field,vecs,idx):
  l = [field]
  for vec in vecs:
    tmp = vec.summary()[field]
    l.append(tmp[idx] if idx is not None else tmp)
  return l


##############################################################################
# A single column of uniform data, possibly lazily computed
# 
class Vec(object):
  def __init__(self, name, expr):
    assert isinstance(name,str)
    assert isinstance(expr,Expr)
    self._name = name  # String
    self._expr = expr  # Always an expr
    expr._name = name  # Pass name along to expr

  # Append a value during CSV read, convert to float
  def append(self,data):
    __x__ = data
    try: __x__ = float(data)
    except ValueError: pass
    self._expr._data.append(__x__)
    self._expr._len+=1

  # Print self
  def show(self): return self._name+" "+self._expr.show()
  # Comment out to help in debugging
  #def __str__(self): return self.show()
  # Compute rollup data
  def summary(self):  return self._expr.summary()

  # Basic indexed or sliced lookup
  def __getitem__(self,i):
    if isinstance(i,Vec):  return self.row_select(i)
    e = Expr(i)
    return Expr("[",self,e,length=len(e));

  # Boolean column select lookup
  def row_select(self,vec):
    return Vec(self._name,Expr("[",self,vec,length=-1))

  # Basic indexed set
  def __setitem__(self,b,c):
    if c and len(c) != 1 and len(c) != len(self):
      raise ValueError("Vec len()="+len(self)+" cannot be broadcast across len(c)="+len(c))
    if isinstance(b,Vec):                   # row-wise assignment
      if len(b) != len(self):
        raise ValueError("Vec len()="+len(self)+" cannot be broadcast across len(b)="+len(b))
      self._expr = Expr("=",Expr("[",self._expr,b),c) # Update in-place
      return self
    raise NotImplementedError

  # Basic (broadening) addition
  def __add__(self,i):
    if isinstance(i,Vec):       # Vec+Vec
      if len(i) != len(self):
        raise ValueError("Vec len()="+len(self)+" cannot be broadcast across len(i)="+len(i))
      return Vec(self._name+"+"+i._name,Expr("+",self,i))
    if isinstance(i,(int,float)): # Vec+int
      if i==0: return self        # Additive identity
      return Vec(self._name+"+"+str(i) ,Expr("+",self,Expr(i)))
    raise NotImplementedError

  def __radd__(self,i): return self+i  # Add is associative

  def __eq__(self,i):
    if isinstance(i,Vec):
      if len(i) != len(self):
        raise ValueError("Vec len()="+len(self)+" cannot be broadcast across len(i)="+len(i))
      return Vec(self._name+"=="+i._name,Expr("==",self,i))
    if isinstance(i,(int,float)): # Vec+int
      return Vec(self._name+"=="+str(i) ,Expr("==",self,Expr(i)))
    raise NotImplementedError

  def __lt__(self,i):
    if isinstance(i,Vec):
      if len(i) != len(self):
        raise ValueError("Vec len()="+len(self)+" cannot be broadcast across len(i)="+len(i))
      return Vec(self._name+"<"+i._name,Expr("<",self,i))
    if isinstance(i,(int,float)): # Vec+int
      return Vec(self._name+"<"+str(i) ,Expr("<",self,Expr(i)))
    raise NotImplementedError

  def __ge__(self,i):
    if isinstance(i,Vec):
      if len(i) != len(self):
        raise ValueError("Vec len()="+len(self)+" cannot be broadcast across len(i)="+len(i))
      return Vec(self._name+">="+i._name,Expr(">=",self,i))
    if isinstance(i,(int,float)): # Vec+int
      return Vec(self._name+">="+str(i) ,Expr(">=",self,Expr(i)))
    raise NotImplementedError

  # Number of rows
  def __len__(self): return self._expr._len

  def mean(self): return Expr("mean",self._expr,None,length=1)

  def asfactor(self): return Vec(self._name,Expr("as.factor",self._expr,None))

  def runif(self,seed=None): return Vec("runif",Expr("h2o.runif",self._expr,Expr(seed if seed else -1)))

##############################################################################
#
# Exprs - 
# - A pending to-be-computed BigData expression.  Does not have a Key
# - An already computed BigData expression.  Does have a Key
# - A small-data computation, pending or not.
#
# Pending computations point to other Exprs in a DAG of pending computations.
# Pointed at by at most one Vec (during construction) and no others.  If that
# Vec goes dead, this computation is known to be an internal tmp; used only in
# building other Exprs.
# 
class Expr(object):
  # Constructor choices:
  # ( "op"  left rite) - pending calc, awaits left & rite being computed
  # ( data  None None) - precomputed local small data
  # (hexkey #num name) - precomputed remote  Big Data
  def __init__(self,op,left=None,rite=None,length=None):
    self._op,self._data = (op,None) if isinstance(op,str) else ("rawdata",op)
    self._name = self._op       # Set an initial name, generally overwritten
    assert self.isLocal() or self.isRemote() or self.isPending(), str(self._name)+str(self._data)
    self._left = left._expr if isinstance(left,Vec) else left
    self._rite = rite._expr if isinstance(rite,Vec) else rite
    assert self._left is None or isinstance(self._left,Expr) or isinstance(self._data,unicode), self.debug()
    assert self._rite is None or isinstance(self._rite,Expr) or isinstance(self._data,unicode), self.debug()
    # Compute length eagerly
    if self.isRemote():
      assert length is not None
      self._len  = length
    elif self.isLocal():
      self._len = len(self._data) if isinstance(self._data,list) else 1
    else:
      self._len = length if length else len(self._left)
    assert self._len is not None
    self._summary = None        # Computed lazily

  def isLocal   (self): return isinstance(self._data,(list,int,float))
  def isRemote  (self): return isinstance(self._data,unicode)
  def isPending (self): return self._data is None
  def isComputed(self): return not self.isPending()

  # Length, generally withOUT triggering an eager evaluation
  def __len__(self):  return self._len

  # Print structure without eval'ing
  def debug(self):
    return ("(["+self._name+"] = "+
            str(self._left._name if isinstance(self._left,Expr) else self._left)+
            " "+self._op+" "+
            str(self._rite._name if isinstance(self._rite,Expr) else self._rite)+
            " = "+str(type(self._data))+")")

  # Eval and print
  def show(self):
    self.eager()
    if isinstance(self._data,unicode):
      j = H2OCONN.Frame(self._data)
      data = j['frames'][0]['columns'][0]['data']
      return str(data)
    return self._data.__str__()
  # Comment out to help in debugging
  #def __str__(self): return self.show()

  # Compute summary data
  def summary(self):
    self.eager()
    if self.isLocal():
      x = self._data[0]
      t = 'int' if isinstance(x,int) else ('enum' if isinstance(x,str) else 'real')
      mins = [min(self._data)]
      maxs = [max(self._data)]
      n = len(self._data)
      mean = sum(self._data)/n if t!='enum' else None
      ssq=0; zeros=0; missing=0
      for x in self._data:  
        if t!='enum': ssq += (x-mean)*(x-mean)
        if x==0:  zeros+=1
        if x is None or (t!='enum' and isnan(x)): missing+=1
      stddev = sqrt(ssq/(n-1)) if t!='enum' else None
      return {'type':t,'mins':mins,'maxs':maxs,'mean':mean,'sigma':stddev,'zeros':zeros,'missing':missing}
    if self._summary: return self._summary
    j = H2OCONN.Frame(self._data)
    self._len = j['frames'][0]['rows']
    self._summary = j['frames'][0]['columns'][0]
    return self._summary

  # Basic indexed or sliced lookup
  def __getitem__(self,i): 
    x = self.eager()
    if self.isLocal(): return x[i]
    if not isinstance(i,int): raise NotImplementedError  # need a bigdata slice here
    # ([ %vec #row #0)
    #j = H2OCONN.Rapids("([ %"+str(self._data)+" #"+str(i)+" #0)")
    #return j['scalar']
    raise NotImplementedError

  # Small-data add; result of a (lazy but small) Expr vs a plain int/float
  def __add__ (self,i): return self.eager()+i
  def __radd__(self,i): return self+i  # Add is associative

  def __del__(self):
    if self.isPending() or self.isLocal(): return  # Dead pending op or local data; nothing to delete
    assert self.isRemote()
    global _CMD;  
    if _CMD is None:
      H2OCONN.Remove(self._data)
    else:
      s = " (del %"+self._data+" #0)"
      global _TMPS
      if _TMPS is None: print "Lost deletes: ",s
      else: _TMPS += s

  # This forces a top-level execution, as needed, and produces a top-level
  # result LOCALLY.  Frames are returned and truncated to the standard head()
  # response - 200cols by 100rows.
  def eager(self):
    if self.isComputed(): return self._data
    # Gather the computation path for remote work, or doit locally for local work
    global _CMD, _TMPS
    assert not _CMD and not _TMPS
    _CMD = ""; _TMPS = ""       # Begin gathering rapids commands
    self._doit()                # Symbolically execute the command
    cmd  = _CMD;  tmps= _TMPS   # Stop  gathering rapids commands
    _CMD = None; _TMPS= None
    if self.isLocal():  return self._data # Local computation, all done
    # Remote computation - ship Rapids over wire, assigning key to result
    if tmps:
      cmd = "(, "+cmd+tmps+")"
    j = H2OCONN.Rapids(cmd)
    if isinstance(self._data,unicode): pass  # Big Data Key is the result
    # Small data result pulled locally
    else: self._data = j['head'] if j['num_rows'] else j['scalar']
    return self._data

  # External API for eager; called by all top-level demanders (e.g. print)
  # May trigger (recursive) big-data eval.
  def _doit(self):
    if self.isComputed(): return

    # Slice assignments are a 2-nested deep structure, returning the left-left
    # vector.  Must fetch it now, before it goes dead after eval'ing the slice.
    # Shape: (= ([ %vec bool_slice_expr) vals_to_assign)
    # Need to fetch %vec out
    assign_vec = self._left._left if self._op=="=" and self._left._op=="[" else None

    global _CMD
    # See if this is not a temp and not a scalar; if so it needs a name
    cnt = sys.getrefcount(self) - 1 # Remove one count for the call to getrefcount itself
    # Magical count-of-4 is the depth of 4 interpreter stack
    py_tmp = cnt!=4 and self._len != 1 and not assign_vec

    if py_tmp:
      self._data = _py_tmp_key() # Top-level key/name assignment
      _CMD += "(= !"+self._data+" "
    _CMD += "("+self._op+" "

    left = self._left
    if left: 
      if left.isPending():  left._doit()
      elif isinstance(left._data,(int,float)): _CMD += "#"+str(left._data)
      elif isinstance(left._data,unicode):     _CMD += "%"+str(left._data)
      else:                                    pass # Locally computed small data
    _CMD += " "

    rite = self._rite
    if rite: 
      if rite.isPending():  rite._doit()
      elif isinstance(rite._data,(int,float)): _CMD += "#"+str(rite._data)
      elif isinstance(rite._data,unicode):     _CMD += "%"+str(rite._data)
      else:                                    pass # Locally computed small data

    if self._op == "+":
      if isinstance(left._data,(int,float)):
        if isinstance(rite._data,(int,float)):    self._data = left+rite
        elif rite.isLocal():                      self._data = [left+x for x in rite._data]
        else:                                     pass
      elif isinstance(rite._data,(int,float)):
        if left.isLocal():                        self._data = [x+rite for x in left._data]
        else:                                     pass
      else:
        if   left.isLocal () and rite.isLocal (): self._data = [x+y for x,y in zip(left._data,rite._data)]
        elif (left.isRemote() or left._data is None) and \
             (rite.isRemote() or rite._data is None): pass
        else: raise NotImplementedError
    elif self._op == "==":
      if isinstance(left._data,(int,float)):
        raise NotImplementedError
      elif isinstance(rite._data,(int,float)):
        if left.isLocal():                        self._data = [x==rite._data for x in left._data]
        else:                                     pass
      else: raise NotImplementedError
    elif self._op == "<":
      if isinstance(left._data,(int,float)):
        raise NotImplementedError
      elif isinstance(rite._data,(int,float)):
        if left.isLocal():                        self._data = [x<rite._data for x in left._data]
        else:                                     pass
      else: raise NotImplementedError
    elif self._op == ">=":
      if isinstance(left._data,(int,float)):
        raise NotImplementedError
      elif isinstance(rite._data,(int,float)):
        if left.isLocal():                        self._data = [x>=rite._data for x in left._data]
        else:                                     pass
      else: raise NotImplementedError
    elif self._op == "[":
      if left.isLocal():                          self._data = left._data[rite._data]
      else:                                       _CMD +=  ' "null"' # Rapids column zero lookup
    elif self._op == "=":
      if left.isLocal(): raise NotImplementedError
      else: 
        if rite is None: _CMD += "#NaN"
    elif self._op == "mean":
      if left.isLocal(): self._data = sum(left._data)/len(left._data)
      else: _CMD += " #0 %TRUE" # Rapids mean extra args (trim=0, rmNA=TRUE)
    elif self._op == "as.factor":
      if left.isLocal():                          self._data = map(str,left._data)
      else:                                       pass
    elif self._op == "h2o.runif":
      if left.isLocal():                          raise NotImplementedError
      else:                                       pass
    else:
      raise NotImplementedError
    # End of expression... wrap up parens
    _CMD += ")"
    if py_tmp:
      _CMD += ")"
    # Free children expressions; might flag some subexpresions as dead
    self._left = None # Trigger GC/ref-cnt of temps
    self._rite = None
    # Keep LHS alive
    if assign_vec:
      if assign_vec._op!="rawdata": # Need to roll-up nested exprs
        raise NotImplementedError
      self._left = assign_vec
      self._data = assign_vec._data

    return

# Global list of pending expressions and deletes to ship to the cluster
_CMD = None
_TMPS= None


##############################################################################
#
# Cluster connection
#
H2OCONN = None # Default connection
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
    j = self._doSafeGet(self.buildURL("Rapids",{"ast":urllib.quote(expr)}))
    if 'error' in j and j['error']: raise ValueError(j['error'])
    return j

  def Frame(self,key):
    return self._doSafeGet(self.buildURL("3/Frames/"+str(key),{}))

  def doModel(self,model,x,train_frame,test_frame=None,**kwargs):
    kwargs['response_column'] = x
    kwargs['training_frame'] = train_frame
    if test_frame: kwargs['validation_frame'] = test_frame
    j = self._doJob(self._doSafeGet(self.buildURL(model,kwargs)))
    j = self._doSafeGet(self.buildURL("3/Models/"+j['dest']['name'],{}))
    return j['models'][0]

  def Metrics(self,model,dataset):
    return self._doSafeGet(self.buildURL("3/ModelMetrics/models/"+model,{}))['model_metrics']

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
      if j['http_status']==404 or j['http_status']==500: 
        if j['msg']: raise ValueError(j['msg'])
        if 'stacktrace' in j: 
          raise ValueError(j['stacktrace'])
        raise ValueError(j)
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
#
# Simple Model Wrapper
#
class H2OModel(object):
  def __init__(self,model,dataset,x,validation_dataset=None,**kwargs):
    if not isinstance(dataset,H2OFrame):  raise ValueError("dataset must be a H2OFrame not "+str(type(dataset)))
    self.dataset = dataset
    if not dataset[x]: raise ValueError(x+" must be column in "+str(dataset))
    self.x = x
    # Send over the frame, and validation frame if any
    fr  = _send_frame(dataset)
    vfr = _send_frame(validation_dataset) if validation_dataset else None
    # Do the big job
    self._model = H2OCONN.doModel(model,x,fr,vfr,**kwargs)
    H2OCONN.Remove(fr)
    if validation_dataset:
      H2OCONN.Remove(vfr)

  def metrics(self,dataset=None):
    return H2OCONN.Metrics(self._model['key']['name'],dataset)

##############################################################################
class H2OGBM(H2OModel):
  def __init__(self,dataset,x,validation_dataset=None,**kwargs):
    H2OModel.__init__(self,"GBM",dataset,x,validation_dataset,**kwargs)

class H2ODeepLearning(H2OModel):
  def __init__(self,dataset,x,validation_dataset=None,**kwargs):
    H2OModel.__init__(self,"DeepLearning",dataset,x,validation_dataset,**kwargs)

##########################################################################

# Send over a frame description to H2O
def _send_frame(dataset):
  # Send over the frame
  fr = _py_tmp_key()
  cbind = "(= !"+fr+" (cbind "
  for vec in dataset._vecs:
    cbind += "%" + vec._expr.eager() + " "
  cbind += "))"
  H2OCONN.Rapids(cbind)
  # And frame columns
  colnames = "(colnames= %"+fr+" {(: #0 #"+str(len(dataset._vecs)-1)+")} {"
  for vec in dataset._vecs:
    colnames += vec._name+";"
  colnames = colnames[:-1]+"})"
  H2OCONN.Rapids(colnames)
  return fr

# Return a unique h2o key obvious from python
def _py_tmp_key():  return unicode("py"+str(uuid.uuid4()))

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

