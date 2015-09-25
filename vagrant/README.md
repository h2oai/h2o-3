# H2O Vagrant Development Environment

You can use the `Vagrantfile` and `bootstrap.sh` in this directory to create a Ubuntu 14.04 LTS (Trusty Tahr) virtual machine up and running, with the correct developer dependencies.


## Instructions 

1. Install [VirtualBox](https://www.virtualbox.org/).
2. Install [Vagrant](http://www.vagrantup.com/downloads).
3. Create a working directory, fetch scripts, setup the virtual machine and `ssh` into it.

        mkdir development
        cd development
        curl -sL https://raw.githubusercontent.com/h2oai/h2o-3/master/vagrant/Vagrantfile
        curl -sL https://raw.githubusercontent.com/h2oai/h2o-3/master/vagrant/bootstrap.sh
        vagrant up
        vagrant ssh

If everything goes correctly, you'll now be inside a virtual machine with git, Java, Python, R, Node.js installed, and with H2O's sources (master) fetched and built in `~/h2o-3`.

