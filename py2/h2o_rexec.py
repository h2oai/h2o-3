
import h2o_exec as h2e
# from h2o_exec import rUnary, rBinary, rCall, rSequence, rColon, rAssign, rNum, rExec

def rUnary(unary, rhs):
    return "%s %s" % (unary, rhs)

def rBinary(binary, operand1, operand2):
    return "%s %s %s" % (binary, operand1, operand2)

def rCall(function, *operands):
    return "(%s %s)" % (function, " ".join(map(str,operands)))

def rSequence(*operands):
    return "{%s}" % (function, ";".join(map(str,operands)))

def rColon(a, b):
    # we always add $ to a here?
    if a is None:
        a = '"null"'
    if b is None:
        b = '"null"'
    return '(: %s %s)' % (a, b) 

# row/col can be numbers, ranges, vectors. verify legal?
def rFrame(a, row, col):
    # we always add $ to a here?
    if row is None:
        row = '"null"'
    if col is None:
        col = '"null"'
    return '([ $%s %s %s)' % (a, row, col)

# row/col can be numbers, ranges, vectors. verify legal?
# should Frame call this for single arg case?
def rVector(a, row):
    # we always add $ to a here?
    if row is None:
        row = '"null"'
    return '([ $%s %s)' % (a, row)

# args should be individual strings, not lists
def rAssign(lhs, rhs):
    return "(= !%s %s)" % (lhs, rhs)

def rNum(number):
    return "#%s" % number

def rKey(key):
    return "$%s" % key

def rExec(execExpr, timeoutSecs=30):
    resultExec, result = h2e.exec_expr(execExpr=execExpr, timeoutSecs=30)
    return resultExec, result 


