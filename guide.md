# CIA 2 - CI/CD Pipeline: Step-by-Step Guide

Nov 5, 2026 · @hiiiii

Your ma'am wants Jenkins, so the pipeline is now a **Jenkinsfile** that runs on a **Jenkins server you build on an Azure VM**. You will do these things in order: run the app on your laptop, push it to GitHub, create an Azure registry and Kubernetes cluster, set up the Jenkins server, connect Jenkins to GitHub, the registry and the cluster, push once to watch the whole pipeline run in Jenkins' Stage View, then change one line and watch it redeploy. Every step below says what you type, **what it does and why**, and how you would do the same thing by hand, so you can answer any "change this manually" question.

**One honest caveat:** I could not run Maven, Jenkins or Azure from my environment. The YAML and Ansible files were syntax-checked and the Jenkinsfile's structure was checked, but the Java code and the Jenkinsfile have not been run end to end. Part B step 3 (Maven tests) and Part H (first Jenkins run) are where you confirm they work. If anything fails, paste me the error text and I will fix the file.

## 1. The big picture

**Jenkins is an automation server.** You tell it (in a file called `Jenkinsfile`) the stages to run. Every time you push code to GitHub, GitHub pings Jenkins, Jenkins pulls the code and runs the stages top to bottom, and its **Stage View** shows each stage as a box that turns green or red. That coloured row of boxes is the "entire pipeline shown fully" your ma'am wants to see.

```
Your laptop --git push--> GitHub --webhook--> Jenkins server (Azure VM)
                                               |
   Stage 1  Checkout            pull the code, note the commit ID
   Stage 2  Compile & Package   mvn clean package   -> app.jar
   Stage 3  Unit Tests          mvn test             -> 8 tests, report in Jenkins
   Stage 4  Docker Build & Tag  docker build         -> image :<commit>
   Stage 5  Push to Registry    docker push          -> Azure Container Registry
   Stage 6  Deploy (Ansible)    ansible-playbook     -> kubectl apply -> AKS
   Stage 7  Verify              curl the public IP   -> new commit ID is live
                                               |
                                               v
                        Azure Kubernetes Service: 2 pods + LoadBalancer -> browser
```

| Tool | Its job here | Why |
| --- | --- | --- |
| Git + GitHub | Holds the code; a push is the trigger | Free, public repo is instructor-accessible |
| **Jenkins** | Runs the 7 stages and shows them | What your ma'am asked for; runs on your own server, so you control everything |
| Maven | Compiles, tests, packages the jar | Required; standard for Java |
| JUnit | The 8 automated tests | Comes with Spring Boot |
| Docker | Packs the app and Java into one image | Same image runs anywhere |
| Azure Container Registry | Stores each image version | Same cloud as the cluster |
| Azure Kubernetes Service | Runs 2 copies of the app, updates them safely | Managed Kubernetes |
| Ansible | (1) sets up the Jenkins VM, (2) runs the deployment | Repeatable configuration instead of hand-typed steps |

**Why the image tag is the commit ID:** `latest` never tells you what is running. If the tag is `a1b2c3d` and the web page shows `a1b2c3d`, you have proof the new code is live.

## 2. Parts A and B: your laptop

### Part A: install the tools

1. **Git, JDK 17, Maven, Docker Desktop.** Check each: `git --version`, `java -version`, `mvn -v`, `docker --version`.
2. **Azure CLI and kubectl.** On Mac: `brew install azure-cli kubectl`. Check: `az version`, `kubectl version --client`.
3. **Azure account.** Sign up for **Azure for Students** with your university email (about $100 credit, no card). If it will not verify, use the free trial, which asks for a card. This is the slowest step, so start it first.
4. A GitHub account (you have `anann-yeah`).

### Part B: run it locally before any cloud

If it fails on your laptop it will fail in Jenkins, and Jenkins is slower to debug.

1. Unzip `cicd-demo.zip`, then `cd cicd-demo`. Skim `HomeController.java` and the two test files.
2. **Build and test:** `mvn clean verify`. Expect `Tests run: 8, Failures: 0` and `BUILD SUCCESS`. *What it does:* Maven downloads dependencies, compiles, runs the tests, builds `target/app.jar`. Screenshot this.
3. **Run the app:** `mvn spring-boot:run`, open http://localhost:8080. You see the page with version `dev`.
4. **Test the Docker image:** `docker build --build-arg APP_VERSION=local1 -t cicd-demo:local .` then `docker run --rm -p 8080:8080 cicd-demo:local`. The page now shows `local1`. *What it does:* builds the image from the `Dockerfile` and runs it as a container, port 8080 inside mapped to 8080 on your laptop. Stop it with Ctrl+C.
5. **Create the GitHub repo:** on github.com choose New repository, name `cicd-demo`, **Public**, no README.
6. **Push:**

```
git init -b main
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/anann-yeah/cicd-demo.git
git push -u origin main
```

Nothing runs yet, because Jenkins does not exist. You will edit one line in the `Jenkinsfile` (your registry name) after Part C, then push again.

## 3. Part C: Azure registry and Kubernetes cluster

Run in your laptop terminal. Pick your own `ACR` name: globally unique, lowercase letters and numbers only.

```
az login
az provider register --namespace Microsoft.ContainerService
az provider register --namespace Microsoft.ContainerRegistry

RG=cicd-demo-rg
LOC=centralindia
ACR=cicddemoanannya
AKS=cicd-demo-aks

az group create -n $RG -l $LOC
az acr create -g $RG -n $ACR --sku Basic --admin-enabled true
az aks create -g $RG -n $AKS --node-count 1 --node-vm-size Standard_B2s \
  --generate-ssh-keys --attach-acr $ACR
```

| Command | What it does | Manual equivalent |
| --- | --- | --- |
| `az login` | Opens a browser so you sign in to Azure | Azure portal sign-in |
| `az group create` | Makes the resource group, a folder holding everything so one delete removes it all | Portal: Resource groups, Create |
| `az acr create` | Makes the private image store; `--admin-enabled` gives a username and password Jenkins will use | Portal: Container registries, Create |
| `az aks create` | Makes the Kubernetes cluster with 1 worker machine; `--attach-acr` lets it pull your images | Portal: Kubernetes services, Create |

Wait 5 to 10 minutes for the cluster. Then check it:

```
az aks get-credentials -g $RG -n $AKS
kubectl get nodes
```

One node in `Ready` state means the cluster works. Screenshot the Azure portal resource group and this output.

**Collect three values now; Jenkins needs them in Part E.**

```
az acr show -n $ACR --query loginServer -o tsv
az acr credential show -n $ACR --query "[username, passwords[0].value]" -o tsv
az aks get-credentials -g $RG -n $AKS --admin --file ~/kubeconfig-ci
```

1. The first prints your registry address, like `cicddemoanannya.azurecr.io`. **Open `Jenkinsfile`, replace `YOUR_REGISTRY_NAME.azurecr.io` with it, then commit and push:** `git add . && git commit -m "Set registry" && git push`.
2. The second prints the registry username (line 1) and password (line 2).
3. The third saves a key file called `~/kubeconfig-ci` that lets Jenkins control the cluster. Keep it outside the repo and never commit it.

If something fails: **RequestDisallowedByAzure** means the region is not allowed for student accounts (the error lists allowed ones; change `LOC`); **VM size not available** means run `az vm list-skus -l $LOC --size Standard_B2 -o table` and choose another 2 vCPU size; **provider not registered** means wait a minute and retry.

## 4. Part D: build the Jenkins server

Jenkins needs a machine that is always on and reachable from the internet (GitHub must be able to call it). You will rent a small Azure VM and let an Ansible playbook install everything on it.

### Step 1: create the VM (laptop terminal)

```
az vm create -g $RG -n jenkins-vm --image Ubuntu2404 --size Standard_B2ms \
  --admin-username azureuser --generate-ssh-keys --public-ip-sku Standard
az vm open-port -g $RG -n jenkins-vm --port 8080 --priority 1010
az vm show -d -g $RG -n jenkins-vm --query publicIps -o tsv
```

*What these do:* the first creates an Ubuntu server with 8 GB RAM (Jenkins plus Maven plus Docker builds need more than 4 GB to be comfortable) and a fixed public IP; the second opens port 8080 in the firewall, which is where Jenkins' web page lives; the third prints the IP. Call it `JENKINS_IP`. If the size is unavailable, try `Standard_B2s` (it works but is slower).

### Step 2: log in and run the Ansible playbook (on the VM)

```
ssh azureuser@JENKINS_IP
sudo apt update && sudo apt install -y git ansible-core
git clone https://github.com/anann-yeah/cicd-demo.git
cd cicd-demo
sudo ansible-playbook -i ansible/inventory.ini ansible/setup-jenkins-server.yml
```

The last command takes 3 to 6 minutes and ends by printing the **initial admin password**. Copy it.

The playbook is **configuration management**: it describes the server you want, and Ansible makes it so. Here is what each task does and what you would type by hand:

| Playbook task | What it achieves | By hand |
| --- | --- | --- |
| Install packages | Java 21 (Jenkins needs it), Maven, Git, Docker, Ansible | `sudo apt install openjdk-21-jdk maven git docker.io ansible-core` |
| Install kubectl | The tool that talks to Kubernetes | `sudo snap install kubectl --classic` |
| Symlink kubectl | The Jenkins service cannot see `/snap/bin`, so link it into `/usr/local/bin` | `sudo ln -s /snap/bin/kubectl /usr/local/bin/kubectl` |
| Download key, add repo | Tells apt where to get Jenkins and trusts its signature | `wget` the key, then add a line to `/etc/apt/sources.list.d/jenkins.list` |
| Install Jenkins | The Jenkins server itself | `sudo apt install jenkins` |
| Add `jenkins` to `docker` group | Lets pipeline stages run `docker` commands | `sudo usermod -aG docker jenkins` then restart Jenkins |
| Start and enable services | Run now and on every reboot | `sudo systemctl enable --now docker jenkins` |
| Read the password | Shows the first-login password | `sudo cat /var/lib/jenkins/secrets/initialAdminPassword` |

Check the server is ready (still on the VM):

```
java -version
mvn -v
docker --version
kubectl version --client
ansible --version
sudo -u jenkins docker ps
```

The last one must print an empty table, not "permission denied". That proves Jenkins can use Docker.

### Step 3: unlock Jenkins (browser)

1. Open `http://JENKINS_IP:8080`.
2. Paste the initial admin password, press Continue.
3. Choose **Install suggested plugins** and wait. This includes Git, Pipeline and **Pipeline Stage View**, the plugin that draws the stage boxes.
4. Create your admin user (username and password you will remember), then Save and Continue.
5. Keep the Jenkins URL it suggests (`http://JENKINS_IP:8080/`), then Start using Jenkins.

Screenshot the Jenkins dashboard and the Azure portal showing `jenkins-vm`.

## 5. Parts E, F and G: connect Jenkins to everything

### Part E: store the secrets in Jenkins

Jenkins keeps passwords in an encrypted **credentials store** and hides them in logs. The Jenkinsfile refers to them by **ID**, so the IDs must be typed exactly.

In Jenkins: **Manage Jenkins, Credentials, System, Global credentials (unrestricted), Add Credentials**. Add two:

| Kind | Fill in | ID (exact) |
| --- | --- | --- |
| Username with password | Username and password from `az acr credential show` | `acr-credentials` |
| Secret file | Choose the file `~/kubeconfig-ci` from your laptop | `kubeconfig` |

The Jenkinsfile uses them like this: `withCredentials([usernamePassword(credentialsId: 'acr-credentials', ...)])` hands the registry login to the push stage, and `withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')])` gives the deploy stage a temporary copy of the cluster key. Both disappear when the stage ends.

### Part F: create the pipeline job

1. Dashboard, **New Item**, name `cicd-demo-pipeline`, choose **Pipeline**, OK.
2. Under **Build Triggers** tick **GitHub hook trigger for GITScm polling**.
3. Under **Pipeline** set Definition to **Pipeline script from SCM**.
4. SCM: **Git**. Repository URL: `https://github.com/anann-yeah/cicd-demo.git` (no credentials needed for a public repo).
5. Branch Specifier: `*/main`.
6. Script Path: `Jenkinsfile`.
7. **Save**.

*Why "script from SCM":* the pipeline definition lives in Git next to the code, so it is versioned and anyone can read it. If you pasted the script into Jenkins instead, it would not be in your repository and your instructor could not see it.

### Part G: tell GitHub to call Jenkins (the webhook)

1. GitHub repo, **Settings, Webhooks, Add webhook**.
2. Payload URL: `http://JENKINS_IP:8080/github-webhook/` (the final slash matters).
3. Content type: `application/json`. Events: **Just the push event**. Active ticked. **Add webhook**.
4. Reload the page. A green tick next to the delivery under **Recent Deliveries** means GitHub reached Jenkins. A red mark means port 8080 is blocked or the IP is wrong. Screenshot the green tick.

The Jenkinsfile also has `pollSCM('H/2 * * * *')`, so even if the webhook breaks, Jenkins checks GitHub every couple of minutes and still starts the build. You would mention both in the viva: the webhook is instant (push model), polling is the fallback (pull model).

## 6. Parts H and I: run it, then change it

### Part H: the first run

1. Open the job `cicd-demo-pipeline` and click **Build Now** (the first run must be started by hand so Jenkins registers the triggers from the Jenkinsfile).
2. The **Stage View** grid appears with seven columns: Checkout, Compile & Package, Unit Tests, Docker Build & Tag, Push to Registry, Deploy to Kubernetes, Verify Deployment. Each fills green as it passes. **This is the screenshot that shows the entire pipeline.**
3. Click the build number, then **Console Output**. Every command and its result is logged with a timestamp. Look for `Tests run: 8`, `docker push`, the Ansible play recap, and finally `SUCCESS: version <commit> is live at http://<ip>`.
4. Click **Test Result** on the build page for the 8 test cases as a table.
5. Copy the `APP URL` from the console, open it in a browser, and screenshot the page with the address bar visible.
6. Collect the cluster evidence on your laptop:

```
kubectl get deployment,pods,svc -o wide
az acr repository show-tags -n $ACR --repository cicd-demo -o table
```

The first deploy takes a few extra minutes while Azure creates the public IP. If a stage turns red, click it and read its log; Part 11 lists the usual causes.

### Part I: the change and redeployment demo

This is what you show live. Do it in this order.

1. **Before:** open the app URL and note the message and version ID. In a laptop terminal run `kubectl get pods -w` so you can watch pods change.
2. **Change:** edit `src/main/resources/application.properties`. Change `v1` to `v2` in `app.message`. Keep the words "CI/CD pipeline" because a test checks for them.
3. **Commit and push:**

```
git add .
git commit -m "Change home page message to v2"
git push
```

4. **Pipeline starts by itself:** switch to Jenkins. Within seconds a new build appears in Build History and the Stage View starts filling. Nobody clicked anything.
5. **New image published:** run `az acr repository show-tags -n $ACR --repository cicd-demo -o table`; the new commit ID is listed beside the old one.
6. **Kubernetes replaces pods:** in the `-w` window new pods appear, become `Running`, and old ones terminate one at a time. Also try `kubectl rollout history deployment/cicd-demo`.
7. **Change visible:** refresh the browser. The text says v2 and the version ID matches the new commit. The last Jenkins stage already proved this by checking the commit ID at `/api/info`.

Tip: practise this twice. The whole loop takes about 5 minutes.

## 7. By hand vs automatic, and "change this for me" questions

A pipeline is just **the commands you would type yourself, written down and run in order by a server**. If you can do each stage by hand, you understand the pipeline. Replace `REG` with your registry address and `abc1234` with a commit ID.

| Stage | What Jenkins runs | What you would type by hand |
| --- | --- | --- |
| 1. Checkout | `checkout scm`, `git rev-parse --short=7 HEAD` | `git clone <repo>` then `git rev-parse --short=7 HEAD` |
| 2. Compile & Package | `mvn -B clean package -DskipTests` | The same command in the project folder |
| 3. Unit Tests | `mvn -B test` and `junit ...` | `mvn test`, then open `target/surefire-reports` |
| 4. Docker Build & Tag | `docker build --build-arg APP_VERSION=... -t REG/cicd-demo:abc1234 .` | The same command |
| 5. Push | `docker login` with stored credentials, then `docker push` | `docker login REG`, then `docker push REG/cicd-demo:abc1234` |
| 6. Deploy | Ansible playbook, which runs `kubectl apply` for the Service and Deployment, then `kubectl rollout status` | `kubectl apply -f k8s/service.yaml`, edit the image in `k8s/deployment.yaml`, `kubectl apply -f k8s/deployment.yaml`, `kubectl rollout status deployment/cicd-demo` |
| 7. Verify | A loop that reads the Service's IP and curls `/api/info` | `kubectl get svc cicd-demo`, then `curl http://<ip>/api/info` |

### If your ma'am asks you to change something

Expect examiners to say "change this and show me". Each row is a request, the file you edit, and how it takes effect.

| Request | Edit | How it goes live |
| --- | --- | --- |
| Show a different message | `app.message` in `application.properties` | `git push`; pipeline redeploys |
| Run 3 pods instead of 2 | `replicas:` in `k8s/deployment.yaml` | `git push`. Or instantly by hand: `kubectl scale deployment/cicd-demo --replicas=3` (the next pipeline run resets it to the file's value) |
| Give pods more memory | `resources.limits.memory` in `k8s/deployment.yaml` | `git push` |
| Add a new stage | Add a `stage('Name') { steps { sh 'echo hi' } }` block in `Jenkinsfile` | `git push`; a new box appears in Stage View |
| Show a failing test stops everything | Change an expected value in a test, e.g. `"Hello, World!"` to `"Hello, Mars!"` | `git push`; **Unit Tests turns red, later stages are skipped, nothing is deployed**. Revert and push to fix |
| Roll back to the previous version | None | `kubectl rollout undo deployment/cicd-demo` (instant); the permanent fix is `git revert HEAD` and push |
| Restart the pods | None | `kubectl rollout restart deployment/cicd-demo` |
| Run the pipeline without pushing | None | Jenkins job, **Build Now** |
| Use a different registry or image name | `ACR_LOGIN_SERVER` or `IMAGE_NAME` in the `Jenkinsfile` environment block | `git push` |
| Build another branch | **Branch Specifier** in the Jenkins job configuration | Save the job |
| Change how often Jenkins polls | `pollSCM('H/2 * * * *')` in the `Jenkinsfile` | `git push` |
| Deploy without Jenkins | None | On the Jenkins VM: `ansible-playbook -i ansible/inventory.ini ansible/deploy.yml -e image=REG/cicd-demo:abc1234` (with `KUBECONFIG` pointing at your key file) |
| See the app's logs | None | `kubectl get pods` then `kubectl logs <pod-name>` |
| Change the app's port | `server.port` in properties, `EXPOSE` in the Dockerfile, `containerPort` and both probe ports in `deployment.yaml`, `targetPort` in `service.yaml` | `git push`. All five must match |

## 8. Every file explained (for the viva)

### The Jenkinsfile, block by block

| Part | What it means |
| --- | --- |
| `pipeline { ... }` | Declarative pipeline: a fixed, readable structure Jenkins validates before running |
| `agent any` | Run on any Jenkins machine; here there is just the one VM |
| `environment { ... }` | Variables available to every stage. Change `ACR_LOGIN_SERVER` to your registry |
| `options { ... }` | `timestamps()` stamps log lines; `disableConcurrentBuilds()` stops two deploys overlapping; `buildDiscarder` keeps only 10 old runs to save disk |
| `triggers { githubPush() pollSCM(...) }` | How a run starts: the GitHub webhook (instant) or polling every \~2 minutes (backup) |
| `stages { stage('...') { steps { ... } } }` | Each `stage` is one box in Stage View; `steps` are what it runs. Stages run in order and the first failure stops the rest |
| `sh '...'` | Runs a shell command on the VM |
| `checkout scm` | Pulls the same repo and commit that triggered the build |
| `env.IMAGE_TAG = ...` | Saves the 7-character commit ID so later stages can use it as the image tag |
| `junit 'target/surefire-reports/*.xml'` | Reads Maven's test results so Jenkins shows the Test Result page and trend graph. In `post { always }` so it runs even when tests fail |
| `withCredentials([...])` | Loads a stored secret into variables for just that block and masks it in logs |
| `post { success / failure / always }` | Actions after the stages: messages, and `docker image prune` to clean disk |

### The other files

| File | What it does | Key lines to explain |
| --- | --- | --- |
| `pom.xml` | Maven build definition | `spring-boot-starter-parent` pins compatible versions; `starter-web` is the web server; `starter-actuator` provides `/actuator/health` for Kubernetes; `<finalName>app` makes the jar `target/app.jar` |
| `GreetingService.java` | Logic | Reads `app.message` and the `APP_VERSION` environment variable (default `dev`); separate from the controller so it is easy to unit-test |
| `HomeController.java` | Web layer | `/` the page, `/api/greet?name=` and `/api/info` JSON; Jenkins' Verify stage curls `/api/info` |
| `GreetingServiceTest`, `HomeControllerTest` | 8 tests | 4 test pure logic; 4 start the app and call it with MockMvc. A failure stops the pipeline before any image exists |
| `application.properties` | Settings | `app.message` is the line you change in the demo; `probes.enabled` creates the liveness and readiness endpoints |
| `Dockerfile` | Image recipe | **Multi-stage:** stage 1 (Maven image) builds the jar; stage 2 (small JRE image) copies only the jar. `COPY pom.xml` before `COPY src` caches dependencies. `ARG APP_VERSION` becomes `ENV` so the app knows its commit. Runs as non-root user 10001 |
| `.dockerignore` | Keeps files out of the build | Excludes `.git`, `target`, `k8s` etc. so builds are fast |
| `k8s/deployment.yaml` | Desired state of the app | `replicas: 2`; `RollingUpdate` with `maxUnavailable: 0` keeps both pods serving until a new one is ready; `readinessProbe` holds back traffic until the app is up; `livenessProbe` restarts a hung container; `resources` set requests and limits; `IMAGE_PLACEHOLDER` is replaced by Ansible |
| `k8s/service.yaml` | Network entry | `type: LoadBalancer` makes Azure create a public IP; `port: 80` maps to `targetPort: 8080`; `selector` picks pods labelled `app: cicd-demo` |
| `ansible/inventory.ini` | Where Ansible runs | `localhost` with `ansible_connection=local`: the Jenkins VM itself |
| `ansible/deploy.yml` | Deploys the app | Checks the `image` variable, applies the Service, applies the Deployment with the placeholder swapped for the real image, waits for `rollout status`, prints pod status. Run with `-e image=...` |
| `ansible/setup-jenkins-server.yml` | Builds the Jenkins server | Installs Java, Maven, Docker, kubectl, Jenkins; adds `jenkins` to the `docker` group; starts services (table in Part 4) |

### What happens during a rolling update

Kubernetes sees the Deployment's image changed and creates a new ReplicaSet. It starts one new pod, waits for the readiness probe to pass, stops one old pod, and repeats. The old ReplicaSet is kept (up to 5), which is why `kubectl rollout undo deployment/cicd-demo` is instant.

## 9. Viva questions with answers

Say these in your own words; these are the points to hit.

1. **What is CI vs CD?** CI automatically builds and tests every change so problems show early. CD automatically delivers the tested build to an environment. Stages 1 to 5 are CI, stages 6 and 7 are CD.
2. **What is Jenkins?** An open-source automation server. It reads the `Jenkinsfile`, runs the stages on an agent, and shows results in Stage View and Console Output.
3. **What is a Jenkinsfile and why keep it in Git?** The pipeline written as code. In Git it is versioned, reviewable and visible to the instructor, and it travels with the code it builds.
4. **Declarative vs scripted pipeline?** Declarative has a fixed structure (`pipeline`, `stages`, `steps`) and is easier to read and validate. Scripted is free-form Groovy. This project uses declarative.
5. **What starts the pipeline?** A push to GitHub triggers a webhook to `/github-webhook/` on Jenkins; `pollSCM` is the backup that checks every \~2 minutes.
6. **Webhook vs polling?** A webhook is push: GitHub tells Jenkins immediately. Polling is pull: Jenkins keeps asking. Webhooks are faster and cheaper.
7. **How are secrets protected in Jenkins?** Stored encrypted in the credentials store, referred to by ID, injected only inside `withCredentials`, and masked in logs. None are in the repo.
8. **Why test before building the image?** To fail fast. A broken build never becomes an image or reaches the cluster. I can demonstrate it by breaking a test.
9. **What does `mvn clean package` do?** Deletes old output, compiles, and packages the jar. `mvn test` runs the tests. `verify` runs everything through to checks.
10. **Why a multi-stage Dockerfile?** The build stage needs Maven and the JDK; the final image needs only a JRE and the jar. Smaller, faster, safer.
11. **Why tag with the commit ID, not `latest`?** Traceability and rollback: each image maps to one commit, and Kubernetes only redeploys when the tag changes.
12. **Image vs container?** An image is a read-only template; a container is a running instance.
13. **Pod vs Deployment vs Service?** A pod runs the container; a Deployment keeps the desired number of pods and manages updates; a Service gives them one stable address and balances load.
14. **How is downtime avoided during an update?** `RollingUpdate` with `maxSurge: 1`, `maxUnavailable: 0` and the readiness probe: a new pod must be ready before an old one stops.
15. **What do the probes do?** Readiness decides whether a pod gets traffic. Liveness decides whether to restart it.
16. **Where is Ansible used and why?** Twice: it builds the Jenkins server from a playbook (repeatable configuration management) and it runs the deployment. Playbooks are idempotent, meaning running them again changes nothing if the state is already right.
17. **Why does the `jenkins` user need the `docker` group?** Jenkins runs as its own user, and only members of that group may talk to the Docker daemon.
18. **How do you roll back?** `kubectl rollout undo deployment/cicd-demo`, or `git revert` and push so the pipeline redeploys the old code.
19. **What would you improve for production?** HTTPS and a proper domain, Jenkins behind a reverse proxy with authentication hardened, a scoped service principal instead of admin credentials, image scanning, a staging environment with approval, monitoring.
20. **How did you prove the new code was live?** The page and `/api/info` show the commit ID baked into the image; the Verify stage waits until it matches the commit Jenkins built.

**Practise the live demo twice.** Rhythm: show the repo and Jenkinsfile, show the green Stage View and the live page, make the v2 change, push, narrate each stage as its box fills, show `kubectl get pods -w`, refresh the browser.

## 10. Screenshot checklist

Full-window shots with the URL bar or terminal command visible. Blur passwords.

- [ ] 1\. `mvn clean verify`: 8 tests passed, BUILD SUCCESS
- [ ] 2\. GitHub repo home page showing `Jenkinsfile`, `Dockerfile`, `k8s`, `ansible`, `src`
- [ ] 3\. Azure portal: resource group with the registry, cluster and `jenkins-vm`
- [ ] 4\. `kubectl get nodes` (or the AKS overview page)
- [ ] 5\. Jenkins dashboard after setup, with the plugins installed
- [ ] 6\. Jenkins Credentials page showing the two IDs (not the secrets)
- [ ] 7\. The pipeline job configuration (Pipeline script from SCM, Git URL, `Jenkinsfile`)
- [ ] 8\. GitHub webhook page with the green tick
- [ ] 9\. **Stage View with all 7 stages green**
- [ ] 10\. Console Output: the test summary, `docker push`, the Ansible recap, and `SUCCESS: version ... is live`
- [ ] 11\. Jenkins Test Result page (8 tests, 0 failures)
- [ ] 12\. Azure portal: ACR, Repositories, `cicd-demo` with its tags
- [ ] 13\. `kubectl get deployment,pods,svc -o wide`: 2/2 ready, pods Running, EXTERNAL-IP set
- [ ] 14\. Browser showing the app at the public IP (v1)
- [ ] 15\. The edit to `application.properties` and the `git push`
- [ ] 16\. A new Jenkins build that started on its own (Build History, "Started by GitHub push")
- [ ] 17\. ACR tags with the old and new commit IDs
- [ ] 18\. `kubectl get pods -w` during the rolling update
- [ ] 19\. Browser showing v2 with the new commit ID
- [ ] 20\. (Bonus) a red Stage View from a deliberately failing test, with later stages skipped

## 11. Report, troubleshooting and cleanup

### Report outline (5 to 8 pages)

Heading block: **CIA 2, Design and Implementation of an End-to-End Cloud-Based CI/CD Pipeline**, then S B Anannya, 23011102075, IoT B. Write it after the demo works so every screenshot is real.

| Section | Pages | What to put in it |
| --- | --- | --- |
| 1. Problem and architecture | 1 | The manual-deployment problem in 3 or 4 sentences, then the diagram from Part 1 redrawn cleanly (draw.io) |
| 2. Tools and justification | 0.5 | The tool table from Part 1. Why Jenkins: self-hosted, full control, plugin ecosystem, Stage View shows every stage, widely used in industry |
| 3. Pipeline stages and purpose | 1 | One short paragraph per stage (7) and why it is in that order |
| 4. Cloud resources and deployment approach | 1 | Resource group, ACR, AKS (1 node, 2 pods), LoadBalancer, the Jenkins VM; rolling updates and why |
| 5. Screenshots and test results | 2 to 3 | The checklist shots, each with a one-line caption; the 8-test result |
| 6. Challenges and solutions | 0.5 to 1 | **Only problems you actually hit**, each with cause and fix |
| 7. Security and limitations | 0.5 to 1 | Use the lists below |

**Security measures you can claim:** credentials in Jenkins' encrypted store and masked in logs; none in the repo; container runs as non-root with privilege escalation disabled; multi-stage image keeps build tools out of production; resource limits on pods; images tagged by commit; tests gate the deployment; Jenkins login required.

**Limitations to state honestly:** Jenkins is served over plain HTTP on an open port 8080; registry admin account and cluster-admin key are long-lived credentials (better: a scoped service principal); the app has no TLS or domain; one cluster node means no real high availability; no image vulnerability scanning; no staging environment or approval gate; no monitoring or alerts; one Jenkins VM is a single point of failure.

### Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| Webhook shows red in GitHub | Port 8080 closed or wrong IP | Re-run `az vm open-port ... --port 8080`; check the URL ends with `/github-webhook/` |
| Push does not start a build | Trigger box not ticked, or first manual run not done | Tick the trigger in the job; click Build Now once; polling will still catch it within \~2 minutes |
| `docker: permission denied` in a stage | `jenkins` not in `docker` group yet | `sudo usermod -aG docker jenkins` then `sudo systemctl restart jenkins` |
| `kubectl: command not found` | Symlink missing | `sudo ln -s /snap/bin/kubectl /usr/local/bin/kubectl` |
| `ansible-playbook: command not found` | Not installed | `sudo apt install -y ansible-core` |
| `Could not find credentials entry with ID ...` | ID typed differently | IDs must be exactly `acr-credentials` and `kubeconfig` |
| Push stage: `unauthorized` | Wrong registry password or `ACR_LOGIN_SERVER` | Recheck both; the server name has no `https://` |
| Build killed or Jenkins very slow | VM out of memory | Use `Standard_B2ms` (8 GB) or add swap |
| Pods `ImagePullBackOff` | Cluster cannot read ACR | `az aks update -g $RG -n $AKS --attach-acr $ACR`, then `kubectl rollout restart deployment/cicd-demo` |
| Pods `CrashLoopBackOff` | App crash or out of memory | `kubectl logs <pod>`; raise the memory limit |
| Verify stage times out | Public IP still being created or readiness failing | `kubectl get svc`, `kubectl describe pod`; wait 2 minutes and Build Now |
| Jenkins cannot find Java | Wrong Java installed before Jenkins | `sudo apt install openjdk-21-jdk`, then `sudo systemctl restart jenkins` |

### Cost and cleanup

You now pay for a cluster node, the Jenkins VM, a public IP and the basic registry; check Azure **Cost Management** for your real figure. To pause between work sessions: `az vm deallocate -g cicd-demo-rg -n jenkins-vm` and `az aks stop -g cicd-demo-rg -n cicd-demo-aks` (restart with `az vm start` and `az aks start`; Jenkins starts by itself). When the viva is completely finished, delete everything: `az group delete -n cicd-demo-rg --yes --no-wait`. Do that only after you have **all screenshots**, because it deletes the live app and the Jenkins server.

## 12. Handing this guide to Claude Code and still learning

Yes, you can give this guide to Claude Code and let it do the typing. It can run `mvn`, `docker`, `git`, `kubectl` and `az` for you and fix errors as they appear. Two cautions first.

1. **Your ma'am will ask you to explain and change things live.** If Claude Code silently does everything you will have a working project and no understanding. So make it a **tutor**: it explains each command before running it and quizzes you after each Part. The prompt below does this.
2. **It will be acting on your Azure account and GitHub.** Make it stop and ask before anything that creates, costs or deletes.

### Set it up

1. In this doc use the export option to download it as **Markdown**, and save it as `GUIDE.md`.
2. Unzip `cicd-demo.zip`, put `GUIDE.md` in the `cicd-demo` folder, and open Claude Code in that folder.
3. Sign in yourself first: run `az login` (opens a browser) and `gh auth login` (or have Git credentials ready) in your own terminal.
4. Paste the prompt:

```
You are my DevOps tutor and pair-programmer. Read GUIDE.md and the repo files in this folder (my CIA 2 guide), then carry it out with me from Part A to Part I.

Rules:
1. Go one Part at a time. Before each command, explain in 1-2 plain sentences what it does and why. After each Part, ask me ONE short question to check I understood, and wait for my answer.
2. Run safe commands yourself (mvn, docker build, git status, kubectl get, az read-only). STOP and ask me before: az login, anything that creates or deletes Azure resources (tell me the rough cost first), git push, and any browser step (Jenkins setup wizard, Jenkins credentials, job creation, GitHub webhook). For browser steps, give me exact click-by-click instructions and wait.
3. Never print passwords, the kubeconfig contents or the Jenkins admin password in the chat. Save them to files in my home folder, outside the repo.
4. If a command fails, show the error, explain the cause in plain words, then fix it. Do not retry blindly.
5. After each stage, tell me which screenshot from the Screenshot checklist to take.
6. At the end, run the Part I change demo with me, then give me a 3-line summary of every file for my viva. Do not delete any Azure resources until I say so.

My details: GitHub user anann-yeah. Choose a unique registry name starting with cicddemo. Region centralindia.
```

### What stays your job

| You | Claude Code |
| --- | --- |
| `az login` and GitHub sign-in | Running `mvn`, `docker`, `kubectl`, `az` commands |
| Jenkins first-run wizard, credentials, job, webhook (do these clicks yourself so you know where everything is) | Editing files, fixing errors, committing |
| Answering its check questions | Explaining each step |
| Taking the screenshots and writing the report in your words | Reading logs and diagnosing failures |

Before the viva, close Claude Code and redo **Part I** once on your own with only Part 7 and Part 9 open. If you can do that, you know it.
