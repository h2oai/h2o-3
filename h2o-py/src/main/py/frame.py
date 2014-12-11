import csv

# A Frame will one day be a pointer to an h2o cloud
# For now, see if we can make (small) 2-D associative arrays,
# and overload everything.
class Frame(object):
  def __init__(self, str=None, file=None, vecs=None):
    if file != None:
      with open(file, 'rb') as csvfile:
        __reader__ = csv.reader(csvfile)
        self._vecs=None
        for row in __reader__:
          if self._vecs==None: 
            self._vecs = []
            for name in row:
              self._vecs.append(Vec(str+name,[]))
          else:
            for i,data in enumerate(row):
              self._vecs[i].append(data)
    elif vecs!= None:
      self._vecs=vecs

  def __str__(self):
    return self._vecs.__repr__()

  def foo(self):
    return "{"+",".join([v.foo() for v in self._vecs])+"}"

  def __getitem__(self,i):
    if isinstance(i,int):
      return self._vecs[i]
    if isinstance(i,str):
      for v in self._vecs:
        if i==v._name:
          return v
      raise ValueError("Name "+i+" not in Frame")
    if isinstance(i,slice):
      return Frame(vecs=self._vecs[i])
    raise NotImplementedError

  def __len__(self):
    return len(self._vecs)

  def __add__(self,i):
    if len(self)==0:
      return self;
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

  def __radd__(self,i):
    return self+i # Add is associative


###
class Vec(object):
  def __init__(self, name, data):
    self._name = name
    self._data = data
    self._len = len(data)

  def eager(self):
    if isinstance(self._data,Expr):
      self._data=self._data.compute()
    return self._data

  def append(self,data):
    __x__ = data
    try:
      __x__ = float(data)
    except ValueError:
      pass
    self._data.append(__x__)
    
  def __repr__(self):
    return self._name+" "+self.eager().__str__()

  def foo(self):
    return self._name+" "+str(self._data)

  def __getitem__(self,i):
    return self._data[i]

  def __add__(self,i):
    if isinstance(i,Vec):
      if len(i) != len(self):
        raise ValueError("Vec len()="+len(self)+" cannot be broadcast across len(i)="+len(i))
      return Vec(self._name+"+"+i._name,Expr("+",self,i))
    if isinstance(i,int):
      if i==0:                  # Additive identity
        return self
      return Vec(self._name+"+"+str(i),Expr("+",self,i))
    raise NotImplementedError

  def __radd__(self,i):
    return self+i # Add is associative

  def __len__(self):
    return len(self._data)

  def __del__(self):
    if Expr==None or not isinstance(self._data,Expr):
      print "DEL of "+self._name

###
class Expr(object):
  def __init__(self,op,left,rite):
    self._op = op
    self._left = left
    self._rite = rite

  def compute(self):
    print "WORK: ",self
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
    return str(self._left._name)+self._op+str(self._rite._name if isinstance(self._rite,Vec) else self._rite)

######################################################
#
#

a = Frame("a.",file="smalldata/iris/iris_wheader.csv")[0:4]

print a[0]._name    # Column header
print a[0][2]       # column 0, row 2 value
print a["a.sepal_len"][2] # Column 0, row 2 value
print "EVAL a[0]+2 is: "   ,a[0]+2        # Add 2 to every element; broadcast a constant
print "EVAL a[0]+a[1] is: ",a[0]+a[1]     # Add 2 columns; broadcast parallel add
print "EVAL mean(a) is: ",sum(a["a.sepal_len"])/len(a[0])

print "FAIL TEST"
try:
  print a["Sepal_len"] # Error, mispelt column name
except ValueError,e:
  pass              # Expected error

print "READ b"
b = Frame("b.",file="smalldata/iris/iris_wheader.csv")[0:4]
print "EVAL c=a+b"
c = a+b
print "EVAL d=c+c+sum(a)"
d = (c+c)+sum(a)
print "EVAL c+a+1"
e = c+(a+1)
print "EVAL e is: ",e
