import csv


# A Frame will one day be a pointer to an h2o cloud
# For now, see if we can make (small) 2-D associative arrays,
# and overload everything.
class Frame(object):
  def __init__(self, lead=None, fname=None, vecs=None):
    # Read a CSV file
    if fname is not None:
      with open(fname, 'rb') as csvfile:
        self._vecs = []
        for name in csvfile.readline().split(','): 
          self._vecs.append(Vec(lead+name.rstrip(), Expr([])))
        for row in csv.reader(csvfile):
          for i,data in enumerate(row):
            self._vecs[i].append(data)
      print "READ: +",len(self),fname
    # Construct from an array of Vecs already passed in
    elif vecs is not None:
      self._vecs = vecs

  # Print [col, cols...]
  def __str__(self): return self._vecs.__repr__()

  # Column selection via integer, string (name) returns a Vec
  # Column selection via slice returns a subset Frame
  def __getitem__(self,i):
    if isinstance(i,int): return self._vecs[i]
    if isinstance(i,str):
      for v in self._vecs:  
        if i==v._name: return v
      raise ValueError("Name "+i+" not in Frame")
    # Slice; return a Frame not a Vec
    if isinstance(i,slice): return Frame(vecs=self._vecs[i])
    raise NotImplementedError

  # Number of columns
  def __len__(self): return len(self._vecs)

  # Addition
  def __add__(self,i):
    if len(self)==0: return self
    if isinstance(i,Frame):
      if len(i) != len(self):
        raise ValueError("Frame len()="+len(self)+" cannot be broadcast across len(i)="+len(i))
      return Frame(vecs=[x+y for x,y in zip(self._vecs,i._vecs)])
    if isinstance(i,Vec):
      if len(i) != len(self._vecs[0]):
        raise ValueError("Vec len()="+len(self._vecs[0])+" cannot be broadcast across len(i)="+len(i))
      return Frame(vecs=[x+i for x in self._vecs])
    if isinstance(i,int):
      return Frame(vecs=[x+i for x in self._vecs])
    raise NotImplementedError

  def __radd__(self,i): return self+i  # Add is associative


########
# A single column of data, possibly lazily computed
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

  # Print self
  def __repr__(self): return self._name+" "+self._expr.__str__()

  # Basic indexed or sliced lookup
  def __getitem__(self,i): return self._expr[i]

  # Basic (broadening) addition
  def __add__(self,i):
    if isinstance(i,Vec):       # Vec+Vec
      if len(i) != len(self):
        raise ValueError("Vec len()="+len(self)+" cannot be broadcast across len(i)="+len(i))
      return Vec(self._name+"+"+i._name,Expr("+",self,i))
    if isinstance(i,(int,float)): # Vec+int
      if i==0: return self        # Additive identity
      return Vec(self._name+"+"+str(i),Expr("+",self,i))
    raise NotImplementedError

  def __radd__(self,i): return self+i  # Add is associative

  def mean(self):
    return Expr("mean",self._expr,None)

  # Number of rows
  def __len__(self): return len(self._expr)

  def __del__(self):
    # Vec is dead, so this Expr is unused by the python interpreter (but might
    # be used in some other larger computation)
    self._expr._name = "TMP_"+self._name

########
#
# A pending to-be-computed expression.  Points to other Exprs in a DAG of
# pending computations.  Pointed at by at most one Vec (during construction)
# and no others.  If that Vec goes dead, this computation is known to be an
# internal tmp; used only in building other Exprs.
# 
class Expr(object):
  def __init__(self,op,left=None,rite=None):
    self._op,self._data = (op,None) if isinstance(op,str) else ("csvfile",op)
    self._left = left._expr if isinstance(left,Vec) else left
    self._rite = rite._expr if isinstance(rite,Vec) else rite
    self._name = self._op # Set an initial name, generally overwritten

  def __len__(self):
    if self._op=="mean": return 1
    if isinstance(self._data,list): return len(self._data)
    return len(self._left) if isinstance(self._left,Expr) else len(self._rite)

  # Print structure without eval'ing
  def debug(self):
    return "(["+self._name+"]="+self._left._name+self._op+str(self._rite._name if isinstance(self._rite,Expr) else self._rite)+")"

  # Eval and print
  def __repr__(self):
    return self.eager().__str__()

  # Basic indexed or sliced lookup
  def __getitem__(self,i): return self.eager()[i]

  # Small-data add; result of a (lazy but small) Expr vs a plain int/float
  def __add__ (self,i): return self.eager()+i
  def __radd__(self,i): return self+i  # Add is associative

  def __del__(self):
    if self._data is None: print "DELE: Expr never evaluated:",self._name
    if isinstance(self._data,list):
      global _CMD;  
      s = "DELE: "+self._name+"; "
      if _CMD:  _CMD += s  # Tell cluster to delete temp as part of larger expression
      else:  print s       # Tell cluster to delete now

  def eager(self):
    if self._data is None:
      global _CMD; assert not _CMD;  _CMD = "{";  self._doit();  print _CMD+"}"; _CMD = None
    return self._data

  # External API for eager; called by all top-level demanders (e.g. print)
  # May trigger (recursive) big-data eval.
  def _doit(self):
    if self._data is not None: return
    if isinstance(self._left,Expr): self._left._doit()
    if isinstance(self._rite,Expr): self._rite._doit()
    if self._op == "+":
      if isinstance(self._left,(int,float)):
        if isinstance(self._rite,(int,float)):  # Small data
          lname, rname = None,None  
          self._data = self._left+self._rite
        else:
          lname, rname = str(self._left), self._rite._name
          self._data = [self._left+x for x in self._rite._data]
      elif isinstance(self._rite,(int,float)):
        lname, rname = self._left._name, str(self._rite)
        self._data = [x+self._rite for x in self._left._data]
      else:
        lname, rname = self._left._name, self._rite._name
        self._data = [x+y for x,y in zip(self._left._data,self._rite._data)]
    elif self._op == "mean":
      lname, rname = self._left._name, None
      self._data = sum(self._left._data)/len(self._left._data)  # Stores a small data result
    else:
      raise NotImplementedError
    if lname:
      global _CMD
      _CMD += self._name+"= "+lname+" "+self._op+" "+str(rname)+"; "
    assert self._data is not None
    self._left = None # Trigger GC/ref-cnt of temps
    self._rite = None
    return

# Global list of pending expressions and deletes to ship to the cluster
_CMD = None
