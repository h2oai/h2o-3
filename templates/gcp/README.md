H2O Open Source clusters on GCP
===============================
Terraform deployment template to spin up a 3 node H2O Open Source cluster in GCP. 

This template is a work in progress and is provided without any warrenty or support. You are free to refer/modify it as you need.

Create a GCP Project
--------------------
- Login to [GCP Console](https://console.cloud.google.com/)
- Create a new Project
- Ensure [Billing is enabled](https://cloud.google.com/billing/docs/how-to/modify-project?authuser=1) for the project
- Enable needed APIs and services (link is on the top of project dashboard)
- **!! NOTE !!** - For this document Project name was `steamwithdataprioc`. Had to use an existing project with name that does not relate to the work done. You can use any project name you want, ens

Setup gcloud cli
----------------
- Follow steps to install [Google Cloud SDK](https://cloud.google.com/sdk/docs/quickstart)
- Since I want to use gcloud SDK with office and personal accounts I follow https://medium.com/google-cloud/how-to-use-multiple-accounts-with-gcloud-848fdb53a39a
	- Office profile `hemen-h2oai`

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

- Setup project. You can create a project from the GUI. Ensure it has billing enabled as erll as services API enabled.
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

Setup Service Account
------------------------

- Create a [Service Account for this Project](https://cloud.google.com/iam/docs/creating-managing-service-accounts#creating)
    ```bash
    gcloud iam service-accounts create steamwithdataproc-sa \
        --description="SteamWithDataproc Service Account" \
        --display-name="steamwithdataproc-sa"
        
    gcloud iam service-accounts list 
    ```
- Create a [service account key for use with terraform](https://cloud.google.com/iam/docs/creating-managing-service-account-keys#creating_service_account_keys). First create a directory structure as shown in the tree command. `cat` is used to check if the key file got created
    ```bash
    $ tree gcp
    gcp
    ├── README.md
    └── main
    $ cd gcp/main
    $ gcloud iam service-accounts keys create gcpkey.json --iam-account steamwithdataproc-sa@steamwithdataproc.iam.gserviceaccount.com
    # cat gcpkey.json
    ```

- This service account key can now be used in Terraform by setting the environment variable `GOOGLE_APPLICATION_CREDENTIALS` [see](https://registry.terraform.io/providers/hashicorp/google/latest/docs/guides/getting_started#adding-credentials) for more information. Alternatively it could also be mentioned in the Terraform code

    ```bash
    export GOOGLE_APPLICATION_CREDENTIALS=`pwd`/gcpkey.json
    ``` 

Create shared GCS storage for TF backend
----------------------------------------
- [Reference](https://cloud.google.com/solutions/managing-infrastructure-as-code#configuring_terraform_to_store_state_in_a_cloud_storage_bucket)
- The TF code currently assumes that GCS bucket to store TF state is already created. We use this approach
	- Ensure the value of variable `gcp_project_name` in `main/variables.tf` is in sync with the project name used in the below command to create the tf state backend bucket
	- `gsutil mb gs://steamwithdataproc-tfstate` to create the bucket
	- `gsutil versioning set on gs://steamwithdataproc-tfstate` to eanble versioning support
- In web browser, select the project and left top menu dropdown select Storage >> Browser and validate the bucket is created.
- An alternate option is to have TF create the bucket, but then would need `terraform apply` in multiple folders as in https://github.com/tasdikrahman/terraform-gcp-examples

- When executing `terraform init` I was getting the error message `"Error: Failed to get existing workspaces: querying Cloud Storage failed: googleapi: Error 403: steamwithdataproc-sa@steamwithdataproc.iam.gserviceaccount.com does not have storage.objects.list access to the Google Cloud Storage bucket., forbidden"`
"
- To overcome this we need to create an IAM policy for this service account to hage the `storage.admin` role. This can be tweaked down if needed.
- `gcloud projects add-iam-policy-binding steamwithdataproc --member serviceAccount:steamwithdataproc-sa@steamwithdataproc.iam.gserviceaccount.com --role roles/storage.admin`

Next we configure [terrafom to use gcs backend for state mangement](https://www.terraform.io/docs/backends/types/gcs.html)
- Add this block to `gcp/main/terraform.tf` file
```  
backend "gcs" {
    bucket = "steamwithdataproc-tfstate"
    prefix = "h2o/terraform"
  }
```


Terraform project structure
---------------------------
The directory structure now is 

```
gcp
├── main                        	# main directory in which terraform apply etc commands are issued
│   ├── gcpkey.json					# Google credential key to be set in GOOGLE_APPLICATION_CREDENTIAL or in the provider block
│   ├── google.tf					# Google provider block
│   ├── main.tf						# main terraform file
│   ├── outoputs.tf
│   ├── terraform.tf				# terraform configuraton. Banckend info also goes here
│   └── variables.tf				# Variable declerations.
├── modules
└── README.md
```

Initialize Terraform
---------------------
- Navigate to `gcp/main` directory and run `terraform init` to initialize terraform. At this point the TF state will be stored in GCP backend in the file named `default.tfstate`
- Additionally a `terraform.tfstate` will be created in the local `.terraform` directory indicating details about the gcs backend and modules 








References
----------
- https://cloud.google.com/solutions/managing-infrastructure-as-code
- https://www.digitalocean.com/community/tutorials/how-to-structure-a-terraform-project
- [Terraform State using AWS ](https://blog.gruntwork.io/how-to-manage-terraform-state-28f5697e68fa)
- [Terraform State using Google](https://gmusumeci.medium.com/how-to-configure-the-gcp-backend-for-terraform-7ea24f59760a)
- [Managed Instance Group Eaxample](https://www.cloudops.com/blog/creating-infrastructure-as-code-with-packer-and-terraform-on-gcp-your-second-step-towards-devops-automation/)







