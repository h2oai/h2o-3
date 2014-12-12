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
          self._vecs.append(Vec(lead+name.rstrip(), []))
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
  def __init__(self, name, data):
    self._name = name  # String
    self._data = data  # Either [float] or Expr (eager) or RX (in-progress)
    self._len = len(data)  # int

  # External API for eager; called by all top-level demanders (e.g. print)
  def eager(self):
    if isinstance(self._data,RX):
      assert self._data._data
      self._data = self._data._data
      return self._data;
    if not isinstance(self._data,Expr):  return self._data
    self._check_subtree_no_rx()  # Assert we're clean
    self._rapids()               # Swap Expr for RX; allow lonely Vecs to go dead but gather work
    #
    # GC/RefCnt happen(s,ed) here, Vecs went dead and recorded death in RX tree
    #
    self._data = self._data.do_it()  # Ship RX over wire for Big Work
    return self._data

  def _check_subtree_no_rx(self): self._data._check_subtree_no_rx()

  # _data is Expr
  def _rapids(self):
    assert isinstance(self._data,Expr)
    self._data = RX(self._name,self._data)
    return self._data

  # Append a value during CSV read, convert to float
  def append(self,data):
    __x__ = data
    try: __x__ = float(data)
    except ValueError: pass
    self._data.append(__x__)

  # Print self; forces eager evaluation
  def __repr__(self): return self._name+" "+self.eager().__str__()

  # Basic indexed or sliced lookup
  def __getitem__(self,i): return self._data[i]

  # Basic (broadening) addition
  def __add__(self,i):
    if isinstance(i,Vec):       # Vec+Vec
      if len(i) != len(self):
        raise ValueError("Vec len()="+len(self)+" cannot be broadcast across len(i)="+len(i))
      return Vec(self._name+"+"+i._name,Expr("+",self,i))
    if isinstance(i,int):       # Vec+int
      if i==0: return self      # Additive identity
      return Vec(self._name+"+"+str(i),Expr("+",self,i))
    raise NotImplementedError

  def mean(self):
    if isinstance(self._data,list):
      return sum(self._data)/len(self._data)
    assert isinstance(self._data,Expr)
    return Vec("mean("+self._name+")",Expr("mean",self,None))

  def __radd__(self,i): return self+i  # Add is associative

  # Number of rows
  def __len__(self): return len(self._data)

  def __del__(self):
    if RX and isinstance(self._data,RX):
      if self._data._data:
        print "DELE: -1",self._name
      else: 
        self._data.is_dead_tmp()
    elif not Expr or not isinstance(self._data,Expr):
      print "DELE: -1",self._name

########
# A pending to-be-computed expression
class Expr(object):
  def __init__(self,op,left,rite):
    self._op = op     # String op
    self._left = left; assert isinstance(left,(Vec,int))
    self._rite = rite; assert isinstance(rite,(Vec,int)) or not rite

  def _check_subtree_no_rx(self):
    if isinstance(self._left,Vec) and isinstance(self._left._data,RX): raise ValueError("Found RX",self)
    if isinstance(self._rite,Vec) and isinstance(self._rite._data,RX): raise ValueError("Found RX",self)
    if isinstance(self._left,Vec) and isinstance(self._left._data,Expr): self._left._check_subtree_no_rx()
    if isinstance(self._rite,Vec) and isinstance(self._rite._data,Expr): self._rite._check_subtree_no_rx()

  def __len__(self):
    if self._op=="mean": return 1
    return len(self._left) if isinstance(self._left,Vec) else len(self._rite)

  def __repr__(self):
    return "("+self._left._name+self._op+str(self._rite._name if isinstance(self._rite,Vec) else self._rite)+")"

########
def deVec(x):
  return (x._rapids() if isinstance(x._data,Expr) else RX(x._name,x._data)) if isinstance(x,Vec) else x

########
# An in-flight computation; a DAG of int/floats, or a tuple of Big Data
# (name,data), or a nested RX
class RX(object):
  # Build a DAG of Big Data work, allowing local temp Vecs to go dead
  def __init__(self, name,x):
    assert isinstance(name,str)
    self._name = name   # String
    if isinstance(x,Expr):
      self._op   = x._op  # String op
      self._left = deVec(x._left); assert isinstance(self._left,(int,float,list,RX))
      self._rite = deVec(x._rite); assert isinstance(self._rite,(int,float,list,RX)) or not self._rite
      self._data = None
    else:
      assert isinstance(x,list)
      self._data = x

  # Flag RX work being dead for a Vec that goes dead at the end of the computation
  def is_dead_tmp(self): self._name = str(self)

  # Do Big Data Work.  Returns a tuple of vec's key/name, and the actual Big Data
  def do_it(self):
    assert not self._data
    if isinstance(self._left,RX):
      if not self._left._data:  self._left.do_it()
      if isinstance(self._left._data,(int,float)):  self._left = self._left._data
    if isinstance(self._rite,RX):
      if not self._rite._data:  self._rite.do_it()
      if isinstance(self._rite._data,(int,float)):  self._rite = self._rite._data
    if self._op == "+":
      if isinstance(self._left,(int,float)):
        if isinstance(self._rite,(int,float)):
          lname = None
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
      print "WORK:",self._name,"=",lname,self._op,rname
    assert self._data
    return self._data


######################################################
#
# Sample use-cases

a = Frame("a.",fname="smalldata/iris/iris_wheader.csv")[0:4]

print a[0]._name    # Column header
print a[0][2]       # column 0, row 2 value
print a["a.sepal_len"][2]  # Column 0, row 2 value
print a[0]+2        # Add 2 to every element; broadcast a constant
print a[0]+a[1]     # Add 2 columns; broadcast parallel add
print sum(a["a.sepal_len"])/len(a[0])
print sum(a)

try: print a["a.Sepal_len"]  # Error, mispelt column name
except ValueError,ex: pass  # Expected error

b = Frame("b.",fname="smalldata/iris/iris_wheader.csv")[0:4]
c = a+b
d = c+c+sum(a)
e = c+a+1
print e
print c

print 1+(a[0]+b[1]).mean()
