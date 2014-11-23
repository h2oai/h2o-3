
import h2o_exec as h2e
import re
# from h2o_exec import xUnary, xBinary, xCall, xSequence, xColon, xAssign, xNum, xExec, xFrame, xVector

def xNum(number):
    if isinstance(number, (list, tuple)):
        raise Exception("xexec xNum doesn't take lists or tuples %s" % number)
    elif isinstance(number, basestring):
        if re.match("#", number):
            return number # already okay
    # do a try/except to decide if it's a number that needs to be a number-string representation
    try: 
        junk = 1.0 + number
        return "#%s" % number
    except:
        return number

def xKey(key):
    if isinstance(key, (list, tuple)):
        raise Exception("xexec xKey doesn't take lists or tuples %s" % key)
    if not isinstance(key, basestring):
        raise Exception("xexec xKey wants to be a string %s" % key)

    # does it already start with '$' ?
    if re.match('\$', frame):
        return key
    else:
        return "$%s" % key

def xUnary(unary='_', rhs='xTemp'):
    rhs = xNum(rhs)
    return "%s %s" % (unary, rhs)

def xBinary(binary='+', operand1='xTemp', operand2='xTemp'):
    operand1 = xNum(operand1)
    operand2 = xNum(operand2)
    return "%s %s %s" % (binary, operand1, operand2)

def xCall(function='sum', *operands):
    # no checking for correct number of params
    print "xCall %s has %s operands" % (function, len(operands))

    for k,v in operands.iteritems():
        if isinstance(v, (list, tuple)):
            raise Exception("xexec xCall operand %s: %s doesn't take lists or tuples %s" % (k, v))
        newOperands[k] = xNum(v)

    if len(operands)!=0:
        return "(%s %s)" % (function, " ".join(newOperands))
    else:
        raise Exception("xexec xCall, no operands: %s" % operands)

# can take multiple parameters, each of which can be number, string, list or tuple
def xSequence(*operands):
    newOperands = []
    for k,v in operands.iteritems():
        # can we handle any operand being a list here too? might be compact
        if not isinstance(v, (list,tuple)):
            newOperands.append(xNum(v))
        else:
            for v2 in v:
                # collapse it into the one new list
                newOperands.append(xNum(v2))
    
    if len(newOperands)!=0:
        return "{%s}" % (function, ";".join(newOperands))
    else:
        raise Exception("xexec xSequence, no operands: %s" % operands)

# a/b can be number or string
def xColon(a='#0', b='#0'):
    # we always add $ to a here?
    if a is None:
        a = '"null"'
    if b is None:
        b = '"null"'
    # if it's not a string, turn the assumed number into a number string
    a = xNum(a)
    b = xNum(b)
    return '(: %s %s)' % (a, b) 

# row/col can be numbers, ranges, vectors. verify legal?
def xFrame(frame='xTemp', row=None, col=None):
    if re.match('\$', frame):
        raise Exception("xExec xFrame adds '$': frame ref shouldn't start with '$' %s" % frame)
    if row is None:
        row = '"null"'
    if col is None:
        col = '"null"'
    # if it's not a string, turn the assumed number into a number string
    a = xNum(row)
    b = xNum(col)
    # we always add $ to a here?. Suppose could detect whether it's already there
    return '([ $%s %s %s)' % (frame, row, col)

# row/col can be numbers, ranges, vectors. verify legal?
# should Frame call this for single arg case?
def xVector(frame='xTemp', row=None):
    if re.match('\$', frame):
        raise Exception("xExec xFrame adds '$': frame ref shouldn't start with '$' %s" % frame)
    if row is None:
        row = '"null"'
    row = xNum(row)
    # we always add $ to a here?. Suppose could detect whether it's already there
    return '([ $%s %s)' % (frame, row)

# args should be individual strings, not lists
def xAssign(lhs='xResult', rhs='xTemp'):
    print "xexec xAssign: %s" % rhs
    rhs = xNum(rhs)
    print "xexec xAssign after xNum: %s" % rhs
    # leading $ is illegal on lhs
    if re.match('\$', lhs):
        raise Exception("xExec xAssign: lhs can't start with '$' %s" % frame)
    # could check that it's a legal key name
    return "(= !%s %s)" % (lhs, rhs)

def xExec(execExpr, timeoutSecs=30):
    resultExec, result = h2e.exec_expr(execExpr=execExpr, timeoutSecs=30)
    return resultExec, result 


