import sys, os
import re

cd = os.path.realpath(os.getcwd())

possible_ns_parent_dir = cd
while(True):
    possible_ns_dir = os.path.join(possible_ns_parent_dir, "h2o-package")
    possible_ns = os.path.join(possible_ns_dir, "NAMESPACE")
    if os.path.exists(possible_ns):
        namespace_dir = possible_ns
        break
    next_possible_ns_parent_dir = os.path.dirname(possible_ns_parent_dir)
    if next_possible_ns_parent_dir == possible_ns_parent_dir:
        break
    possible_ns_parent_dir = next_possible_ns_parent_dir


namespace = open(namespace_dir, 'r')


funcs = {}
for l in namespace:
    if re.match("^export\(", l):
        possible_func = l[7:-2]
        if len(possible_func) > 0:
            funcs[possible_func] = {}


filelist = []

dirs = [i for i in os.listdir(cd) if re.match("^testdir", i)]
for d in dirs:
    for path, names, files in os.walk(d):
        for f in files:
         if re.match("^runit.*\.R$", f):
            val = path + "/" + f
            filelist.append(os.path.join(cd, val))


for path in filelist:
    test = open(path, 'r')
    for i,l in enumerate(test):
        for fun in funcs:
            funcall = fun + "("
            if funcall in l:
                case = str(i+1) + "\t" + l.strip()
                count = l.count('(') - l.count(')')
                while count > 0:
                    l = test.next()
                    case += l.strip()
                    count -= l.count(')') - l.count('(')
                if path in funcs[fun]:
                    funcs[fun][path].append(case)
                else:
                    funcs[fun][path] = [case]



fout = os.path.join(cd, "Usage")
if not os.path.exists(fout):
    os.makedirs(fout)

not_found = list()
for fun in funcs:
    funcount = len(funcs[fun])
    if funcount > 0:
        print(fun + " found in: " + str(funcount) + " files.")
    else:
        not_found.append(fun)
    funf = open(os.path.join(fout, fun+".txt"), 'w')
    funf.write(fun + "\n")
    funf.write("File occurences:\t" + str(len(funcs[fun])) + "\n")
    funf.write("Total occurences:\t" + str(sum(len(funcs[fun][path]) for path in funcs[fun])) + "\n")
    for path in funcs[fun]:
        funf.write("\n" + str(len(funcs[fun][path])) + " use(s) in file: " + path + "\n")
        for case in funcs[fun][path]:
            funf.write(case + "\n")

print "\n" + str(len(not_found)) + " functions not found."
print "No test cases found: " + ", ".join([str(fun) for fun in not_found])
