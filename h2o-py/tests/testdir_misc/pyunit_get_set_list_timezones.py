import sys
sys.path.insert(1, "../../")
import h2o
import random

def get_set_list_timezones(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    origTZ = h2o.get_timezone()
    print "Original timezone: {0}".format(origTZ)

    timezones = h2o.list_timezones()
    zone = timezones[random.randint(0,timezones.nrow()-1),0].split(" ")[1].split(",")[0]
    print "Setting the timezone: {0}".format(zone)
    h2o.set_timezone(zone)

    newTZ = h2o.get_timezone()
    assert newTZ == zone, "Expected new timezone to be {0}, but got {01}".format(zone, newTZ)

    print "Setting the timezone back to original: {0}".format(origTZ)
    h2o.set_timezone(origTZ)

if __name__ == "__main__":
    h2o.run_test(sys.argv, get_set_list_timezones)