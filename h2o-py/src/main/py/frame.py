import csv

# A Frame will one day be a pointer to an h2o cloud
# For now, see if we can make (small) 2-D associative arrays,
# and overload everything.
class Frame(object):
  def __init__(self, str=None, file=None, vecs=None):
    # Read a CSV file
    if file != None:
      with open(file, 'rb') as csvfile:
        self._vecs = []
        for name in csvfile.readline().split(','): 
          self._vecs.append(Vec(str+name.rstrip(),[]))
        for row in csv.reader(csvfile):
          for i,data in enumerate(row): self._vecs[i].append(data)
      print "READ: +",len(self),file
    # Construct from an array of Vecs already passed in
    elif vecs!= None:
      self._vecs=vecs

  # Print [col, cols...]
  def __str__(self):  return self._vecs.__repr__()

  # Column selection via integer, string (name) returns a Vec
  # Column selection via slice returns a subset Frame
  def __getitem__(self,i):
    if isinstance(i,int):   return self._vecs[i]
    if isinstance(i,str):
      for v in self._vecs:  
        if i==v._name:  return v
      raise ValueError("Name "+i+" not in Frame")
    # Slice; return a Frame not a Vec
    if isinstance(i,slice): return Frame(vecs=self._vecs[i])
    raise NotImplementedError

  # Number of columns
  def __len__(self): return len(self._vecs)

  # Addition
  def __add__(self,i):
    if len(self)==0:  return self
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

  def __radd__(self,i):  return self+i # Add is associative


########
# A single column of data, possibly lazily computed
class Vec(object):
  def __init__(self, name, data):
    self._name = name
    self._data = data
    self._len = len(data)

  # Force eager evaluation
  def eager(self):
    if isinstance(self._data,Expr):  self._data=self._data.compute()
    return self._data

  # Append a value during CSV read, convert to float
  def append(self,data):
    __x__ = data
    try:  __x__ = float(data)  
    except ValueError:  pass
    self._data.append(__x__)

  # Print self; forces eager evaluation
  def __repr__(self): return self._name+" "+self.eager().__str__()

  # Basic indexed or sliced lookup
  def __getitem__(self,i):  return self._data[i]

  # Basic (broadening) addition
  def __add__(self,i):
    if isinstance(i,Vec):       # Vec+Vec
      if len(i) != len(self):
        raise ValueError("Vec len()="+len(self)+" cannot be broadcast across len(i)="+len(i))
      return Vec(self._name+"+"+i._name,Expr("+",self,i))
    if isinstance(i,int):       # Vec+int
      if i==0:  return self     # Additive identity
      return Vec(self._name+"+"+str(i),Expr("+",self,i))
    raise NotImplementedError

  def __radd__(self,i):  return self+i # Add is associative

  # Number of rows
  def __len__(self):  return len(self._data)

  def __del__(self):
    if Expr==None or not isinstance(self._data,Expr):
      print "DELE: -1",self._name

########
# A pending to-be-computed expression
class Expr(object):
  def __init__(self,op,left,rite):
    self._op = op
    self._left = left
    self._rite = rite

  def compute(self):
    print "WORK: +1",self
    if self._op == "+":
      if isinstance(self._rite,Vec):
        return [x+y for x,y in zip(self._left.eager(),self._rite.eager())]
      if isinstance(self._rite,int):
        return [x+self._rite for x in self._left.eager()]
      raise NotImplementedError
    raise NotImplementedError

  def __len__(self):
    return len(self._left) if isinstance(self._left,Vec) else len(self._rite)

  def __repr__(self):
    return "("+str(self._left._name)+self._op+str(self._rite._name if isinstance(self._rite,Vec) else self._rite)+")"

######################################################
#
# Sample use-cases

a = Frame("a.",file="smalldata/iris/iris_wheader.csv")[0:4]

print a[0]._name    # Column header
print a[0][2]       # column 0, row 2 value
print a["a.sepal_len"][2] # Column 0, row 2 value
print a[0]+2        # Add 2 to every element; broadcast a constant
print a[0]+a[1]     # Add 2 columns; broadcast parallel add
print sum(a["a.sepal_len"])/len(a[0])

try:   print a["Sepal_len"] # Error, mispelt column name
except ValueError,e:  pass  # Expected error

b = Frame("b.",file="smalldata/iris/iris_wheader.csv")[0:4]
c = a+b
d = c+c+sum(a)
e = c+(a+1)
print e
