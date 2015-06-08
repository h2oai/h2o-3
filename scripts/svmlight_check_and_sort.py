from sys import argv
from operator import itemgetter

if __name__ == "__main__":
    if (len(argv) != 3):
        print("Usage: " + argv[0] + " <input.svm> <output.svm>")
        print("Example:")
        print("input.svm:")
        print("1 24:1 12:1 55:1")
        print("0 84:1 82:1 15:1")
        print("...")
        print("output.svm:")
        print("1 12:1 24:1 55:1")
        print("0 15:1 82:1 84:1")
        print("...")
        exit(1)

    input = argv[1]
    output = argv[2]
    with open(input, "r") as f:
        for line in f:
            splits = line.split()
            target = splits[0]
            features = splits[1:]
            d = {int(a[0]):int(a[1]) for a in [feat.split(':') for feat in features]}
            dsorted = sorted(d.iteritems(), key=itemgetter(0), reverse=False)
            with open(output, "a") as g:
                g.write(target + ' ')
                prev_i=-1
                for (i,j) in dsorted:
                    if(i == 0):
                         print("Column index must be >= 1: " + dsorted)
                         exit(1)
                    if(i == prev_i):
                         print("Repeats found: " + dsorted)
                         exit(1)
                    prev_i = i
                    g.write(str(i)+':'+str(j)+' ')
                g.write('\n')
