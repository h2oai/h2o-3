H2O Open Source clusters on GCP
===============================
Terraform deployment template to spin up a 3 node H2O Open Source cluster in GCP. 

This template is a work in progress and is provided without any warranty or support. You are free to refer/modify it as you need.

There are two distinct parts to this setup
1. Setting up the GCP project, service account, VPC, Subnet etc. Also in this step we create the GCP compute instance we call __Workspace__. All H2O users will need to ssh to this workspace instance to create the H2O cluster in the private subnet (not directly accessible). The workspace instance is in the public subnet and forms the gateway for all communication between the H2O cluster and machine of data scientist.
2. Creating the H2O cluster using the `h2ocluster` tool available on the __Workspace__ instance

Step 1: Main Infrastructure and Workspace Setup
-----------------------------------------------

These activities are performed by someone who have Cloud Admin privileges. In this step we perform pre-required activities using the browser on the GCP console, and then setup `gcloud` sdk tool on a machine where we use `terraform` to get the entire infrastructure setup.

#### Create a GCP Project
- Using a web browser, login to [GCP Console](https://console.cloud.google.com/)
- Create a new Project
- Ensure [Billing is enabled](https://cloud.google.com/billing/docs/how-to/modify-project?authuser=1) for the project
- Enable needed APIs and services (link is on the top of project dashboard)
- **!! NOTE !!** - For this work I reused an existing project with the name `steamwithdataproc`. The name does not directly relate to this work, but ignore that for now.

#### Setup gcloud cli
- Preferably setup the `gcloud` sdk on a linux based machine, ideally used by the Cloud system admin team to manage the cloud infrastructure.
- Follow steps to [install Google Cloud SDK](https://cloud.google.com/sdk/docs/quickstart)
- Create a new gcloud profile and authenticate
	```
	$ gcloud config configurations create hemen-h2oai
	Created [hemen-h2oai].
	Activated [hemen-h2oai].
	$ gcloud auth login
	Your browser has been opened to visit:
	
	    https://acco ..... deleted .... t_account
	
	You are now logged in as [hemen.kapadia@h2o.ai].
	Your current project is [None].  You can change this setting by running:
	  $ gcloud config set project PROJECT_ID
	```

- Setup project. You would have already created a project from the GUI as discussed earlier. Ensure it has billing enabled as well as services API enabled.
	```
	$ gcloud config set project steamwithdataproc
	Updated property [core/project].
	```

- Setup compute region. You can use `gcloud compute regions list` to get a list of available compute regions
    ```
    $ gcloud config set compute/region us-west1
    Updated property [compute/region].
    ```

- Setup compute zone. You can use `gcloud compute zones list` to get a list of available compute zones
    ```
    $ gcloud config set compute/zone us-west1-a
    Updated property [compute/zone].
    ```
- Check all set configurations are as expected
    ```bash
    $ gcloud config list
    [compute]
    region = us-west1
    zone = us-west1-a
    [core]
    account = hemen.kapadia@h2o.ai
    disable_usage_reporting = True
    project = steamwithdataproc

    Your active configuration is: [hemen-h2oai]
    ```

#### Setup Service Account
- A total of 3 service accounts are needed for this to work end to end. Of the three, one is created manually and has the most privileges. The remaining two will are created by the terraform script
    - `steamwithdataproc-sa`
        - This one is created manually as shown below using gcloud
        - It is used to setup the VPC, firewalls etc and also the Workspace instance
        - Needs Compute Admin to create instances and Storage Admin to manage state
    - `workspaceinstate-sa`
        - Terraform creates this
        - The SA assigned to the workspace instance started above. 
        - This SA will then be used by Terraform to control the permissions of starting H2O clusters.
    - `h2ocluster-sa`
        - Terraform creates this
        - This SA will be assigned to each VM instance that forms the H2O cluster nodes
        - Access to google cloud 
- Create a [Service Account for this Project](https://cloud.google.com/iam/docs/creating-managing-service-accounts#creating)
    ```bash
    gcloud iam service-accounts create steamwithdataproc-sa \
        --description="SteamWithDataproc Service Account" \
        --display-name="steamwithdataproc-sa"
        
    gcloud iam service-accounts list 
    ```
- Ensure the Service account has necessary priviledges. Here these may be a bit extra but more fin grained access roles could be given
    - [GCP Roles list](https://cloud.google.com/iam/docs/understanding-roles?authuser=1#compute-engine-roles)
    ```
    gcloud projects add-iam-policy-binding steamwithdataproc --member serviceAccount:steamwithdataproc-sa@steamwithdataproc.iam.gserviceaccount.com --role roles/storage.admin 
    gcloud projects add-iam-policy-binding steamwithdataproc --member serviceAccount:steamwithdataproc-sa@steamwithdataproc.iam.gserviceaccount.com --role roles/compute.admin
    gcloud projects add-iam-policy-binding steamwithdataproc --member serviceAccount:steamwithdataproc-sa@steamwithdataproc.iam.gserviceaccount.com --role roles/iam.serviceAccountAdmin
    gcloud projects add-iam-policy-binding steamwithdataproc --member serviceAccount:steamwithdataproc-sa@steamwithdataproc.iam.gserviceaccount.com --role roles/iam.serviceAccountUser
    gcloud projects add-iam-policy-binding steamwithdataproc --member serviceAccount:steamwithdataproc-sa@steamwithdataproc.iam.gserviceaccount.com --role roles/iam.securityAdmin
    ```

- Create a [service account key for use with terraform](https://cloud.google.com/iam/docs/creating-managing-service-account-keys#creating_service_account_keys). First create a directory structure as shown in the tree command. `cat` is used to check if the key file got created
    ```bash
    $ cd gcp/network
    $ gcloud iam service-accounts keys create gcpkey.json --iam-account steamwithdataproc-sa@steamwithdataproc.iam.gserviceaccount.com
    # cat gcpkey.json
    ```

- This service account key can now be used in Terraform by setting the environment variable `GOOGLE_APPLICATION_CREDENTIALS` [see](https://registry.terraform.io/providers/hashicorp/google/latest/docs/guides/getting_started#adding-credentials) for more information. Alternatively it could also be mentioned in the Terraform code

    ```bash
    export GOOGLE_APPLICATION_CREDENTIALS=`pwd`/gcpkey.json
    ``` 

#### Create shared GCS storage for TF backend
- The TF code currently assumes that GCS bucket to store TF state is already created. We use this approach
	- Ensure the value of variable `gcp_project_name` in `network/main/variables.tf` is in sync with the project name used in the below command to create the tf state backend bucket
	- `gsutil mb gs://steamwithdataproc-tfstate` to create the bucket
	- `gsutil versioning set on gs://steamwithdataproc-tfstate` to eanble versioning support
- In web browser, select the project and left top menu dropdown select Storage >> Browser and validate the bucket is created.
- An alternate option is to have TF create the bucket, but then would need `terraform apply` in multiple folders as in https://github.com/tasdikrahman/terraform-gcp-examples

Next we configure [terrafom to use gcs backend for state mangement](https://www.terraform.io/docs/backends/types/gcs.html)
- Add this block to `gcp/main/terraform.tf` file
```  
backend "gcs" {
    bucket = "steamwithdataproc-tfstate"
    prefix = "h2o/terraform"
  }
```


#### Terraform project structure
The directory structure now is 
```
gcp
├── network                        	
├── h2ocluster                      
```
- `network` directory 
	- contains all Terraform code that will setup the VPC, Subnets, Firewall, Workspace instance, service accounts etc.
	- executed only one time
	- happens on any external machine, possibly a cloud admins laptop
- `h2ocluster` directory
	- this directory is zipped and should be moved to `/opt/h2ocluster` in the workspace system 
	- contains Terraform code that will setup a N node H2O cluster instance in the private subnet when requested by a user.
	- will be executed multiple times by the user to start/stop the cluster.
	- will not be executed directly as terraform apply or destroy. Instead a bash wrapper will be provided to list, create and destroy the custer instance
	- list will use gcloud commands whereas create and destroy will leverage the terraform code in this directory.

#### Terraform init and apply
- Navigate to `gcp/network` directory and run `terraform init` to initialize terraform. 
	- a `terraform.tfstate` will be created in the `network/.terraform` directory with details about the gcs backend and modules
	- the TF state file without any resources is created in GCP backend with the file named `default.tfstate`.
	- `gsutil cat gs://steamwithdataproc-tfstate/h2o/terraform/default.tfstate` to view the content of this initial state
- `terraform apply` can be used to create all the necessary network and workspace resources
- `terraform show` can be used to see the resources state
- `terraform refresh` can be used to update state informaton with the chages in real world infra that happened via Google Web console.
- At this point we trigger a `terraform apply` to create the VPC, public + private subnets, firewall rules, NAT gateways, service account etc. and the main __Workspace__ machine on a GCP Compute instance.


#### Workspace machine
- This is a single machine like a bastion host, in the public subnet of VPC. It should be up and running now. 
- Instances in the public subnet will get an external IP and hence are internet accessible. 
- Instances without a public address are private and as a convention we put them in the private subnet. 
- After `terraform apply` when the workspace machine was created it can be accessed with 
	- `gcloud beta compute ssh --zone "us-west1-a" --project "steamwithdataproc" --ssh-key-file=~/.ssh/google_compute/id_rsa "h2o-instance-workspace"`
- Create SSH key - For the very first time we would not have an ssh key to use. 
	- Assuming that you have completed the `gcloud auth login` step from point 3 above you can run the above command without `--ssh-key-file` option.
	- This will create the files `google_compute_engine`, `google_compute_engine.pub` and `google_compute_engine.knownhosts` files in `$HOME/.ssh` directory.
	- Will work only if in Project >> IAM your user id will have `Compute OS Login` or `Compute OS Admin Login` roles to your member.
- It should be able to access this machine now with the above command
- Additionally, once done with the above command we can then use normal ssh also. Note the username to use when we connect above. You can get this username to use when you ssh above. 
	- `ssh -i ~/.ssh/google_compute_engine hemen_kapadia_h2o_ai@35.247.123.203`

If the Workspace machine is created and you are able to ssh to it, we conclude step 1 of creating the infrastructure setup

Step 2: Creating H2O clusters 
-----------------------------------------------

10. On Workspace Servers
-------------------------
- Once Workspace server is up and running check. `jq --version`, `terraform --version`, `gcloud config list`. All these commands should be working. Additionally `gcloud` should be able to detect the service account that is associated with the Workspace compute instance.
- To avoid the zone prompt for some of the commands used internally by the `h2ocluster` tool set the zone information using `gcloud config set compute/region us-west1`
- Update PATH variable `export PATH="$PATH:/opt/h2ocluster/terraform"`
- Initialise `h2ocluster --help`
- Read the usage of `h2ocluster` tool using `h2ocluster --help`
- Create a cluster `h2ocluster create`
- Once created note the IP and Port information displayed
    ```
    H2O Cluster Information:
    =========================
    Cluster Name: h2o-hemenkap-letxk7f-cluster
    Cluster Size: 3
    Cluster Leader IP and PORT: 10.100.1.2:54321
    Cluster Leader Url: http://10.100.1.2:54321/flow/index.html#
    ```
- Using this info, create an ssh local port forward to the H2O cluster created in the private subnet via the Workspace machine (which is like a bastion). You can select any local port to forward. I used `8888` in this example.
    ```bash
    ssh -i ~/.ssh/google_compute/id_rsa -L 8888:10.100.1.2:54321  hemen_kapadia_h2o_ai@35.247.123.203
    ```
- Open a browser on your laptop and go to URL `http://localhost:8888`. You will see the H2O flow UI.
- If you are running Python or R code to connect to the H2O cluster then the cluster address will be different based on where you code is executing.
    - Workspace machine use `http://10.100.1.2:54321`
    - Local machine/laptop with ssh forwarding use  `http://localhost:8888`


Step 3: Optional create a custom H2O-3 image 
-----------------------------------------------
- To speed up the cluster creation times you can use an image with H2O preloaded on it.
- To create such an image, on the Workspace machine follow the instructions below
  - `cd /opt/h2ocluster/packer/`
  - If needed update the variable values in the file `h2o-gcp-image.json`
  - `packer build h2o-gcp-image.json`
- Once the image is built, it can be used in the terraform code to create H2O-3 clusters.
  - Edit file `/opt/h2ocluster/terraform/terraform.tfvars` and update the value of `h2o_cluster_instance_boot_disk_image` to the name of the packer imge.
- Now the cluster load times will be significantly reduced as compared to the situation where we start from a bare RHEL7 image as the base





Good References
---------------
- [GCP metadata to recognise cluster ](https://medium.com/google-cloud/coordinating-vm-clusters-with-google-compute-engines-metadata-server-d13e4a5075d)
- [GCP Cloud getting started](https://cloud.google.com/solutions/automated-network-deployment-overview)
- [GCP Private VMs](https://cloud.google.com/solutions/building-internet-connectivity-for-private-vms#terraform_2)
- https://cloud.google.com/solutions/managing-infrastructure-as-code
- https://www.digitalocean.com/community/tutorials/how-to-structure-a-terraform-project
- [Terraform State using AWS ](https://blog.gruntwork.io/how-to-manage-terraform-state-28f5697e68fa)
- [Terraform State using Google](https://gmusumeci.medium.com/how-to-configure-the-gcp-backend-for-terraform-7ea24f59760a)
- [Managed Instance Group Eaxample](https://www.cloudops.com/blog/creating-infrastructure-as-code-with-packer-and-terraform-on-gcp-your-second-step-towards-devops-automation/)
- [Assigning a Service Account to a VM instance](https://cloud.google.com/compute/docs/access/create-enable-service-accounts-for-instances#using)
- [Using Groups to manage users and IAM roles](https://cloud.google.com/iam/docs/groups-in-cloud-console)

- [How IAM works](https://cloud.google.com/iam/docs/overview#how_cloud_iam_works)

Useful for Workspace

- [Gcloud Init without Auth in browser](https://stackoverflow.com/questions/42379685/can-i-automate-google-cloud-sdk-gcloud-init-interactive-command)


Useful for Startup Script completion tracking
- [Updating instance Metadata](https://cloud.google.com/compute/docs/storing-retrieving-metadata#updatinginstancemetadata)
	- See the updating on a running instance instead of the 
- [Waiting for Metadata Change](https://cloud.google.com/compute/docs/storing-retrieving-metadata#waitforchange)
- Metadata on GCP instances can be accessed using the metadata url.
- For non GCP instances we can access it as
	- `gcloud compute instances describe h2o-instance-workspace --format='value[](metadata.items.startup-complete)'`
	- We use [gcloud topic filters](https://cloud.google.com/sdk/gcloud/reference/topic/filters) to get the desired value out of the response.
- [Debugging Startup Scripts](https://cloud.google.com/compute/docs/startupscript#viewing_startup_script_logs)

- [Google Cloud Cheatsheet](https://gist.github.com/pydevops/cffbd3c694d599c6ca18342d3625af97#011-service-account)







