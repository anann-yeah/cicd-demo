// Declarative Jenkins pipeline. Jenkins reads this file from the repo on every run.
pipeline {

    // Run on any available Jenkins machine (here: the Jenkins VM itself)
    agent any

    environment {
        // >>> CHANGE THIS to your own registry login server (az acr show -n <name> --query loginServer -o tsv)
        ACR_LOGIN_SERVER = 'YOUR_REGISTRY_NAME.azurecr.io'
        IMAGE_NAME       = 'cicd-demo'
    }

    options {
        timestamps()                                        // time on every log line
        disableConcurrentBuilds()                           // never deploy twice at once
        buildDiscarder(logRotator(numToKeepStr: '10'))      // keep only the last 10 runs
    }

    triggers {
        githubPush()                  // instant trigger from the GitHub webhook
        pollSCM('H/2 * * * *')        // safety net: check GitHub every ~2 minutes
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm                                  // pull the repo at the pushed commit
                script {
                    // Short commit ID becomes the Docker image tag and the app's version
                    env.IMAGE_TAG = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()
                }
                echo "Building commit ${env.IMAGE_TAG}"
            }
        }

        stage('Compile & Package (Maven)') {
            steps {
                sh 'mvn -B clean package -DskipTests'         // produces target/app.jar
            }
        }

        stage('Unit Tests (Maven)') {
            steps {
                sh 'mvn -B test'                               // fails the build if any test fails
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'      // test report shown inside Jenkins
                }
            }
        }

        stage('Docker Build & Tag') {
            steps {
                sh '''
                    docker build --build-arg APP_VERSION="$IMAGE_TAG" \
                        -t "$ACR_LOGIN_SERVER/$IMAGE_NAME:$IMAGE_TAG" \
                        -t "$ACR_LOGIN_SERVER/$IMAGE_NAME:latest" .
                '''
            }
        }

        stage('Push to Registry (ACR)') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'acr-credentials',
                                                  usernameVariable: 'ACR_USER',
                                                  passwordVariable: 'ACR_PASS')]) {
                    sh '''
                        echo "$ACR_PASS" | docker login "$ACR_LOGIN_SERVER" -u "$ACR_USER" --password-stdin
                        docker push "$ACR_LOGIN_SERVER/$IMAGE_NAME:$IMAGE_TAG"
                        docker push "$ACR_LOGIN_SERVER/$IMAGE_NAME:latest"
                        docker logout "$ACR_LOGIN_SERVER"
                    '''
                }
            }
        }

        stage('Deploy to Kubernetes (Ansible)') {
            steps {
                withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                    sh '''
                        kubectl get nodes
                        ansible-playbook -i ansible/inventory.ini ansible/deploy.yml \
                            -e image="$ACR_LOGIN_SERVER/$IMAGE_NAME:$IMAGE_TAG"
                    '''
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                    sh '''
                        echo "Waiting for the public IP..."
                        IP=""
                        for i in $(seq 1 30); do
                            IP=$(kubectl get svc cicd-demo -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
                            [ -n "$IP" ] && break
                            sleep 10
                        done
                        [ -n "$IP" ] || { echo "No external IP assigned"; exit 1; }
                        echo "APP URL: http://$IP"

                        echo "Waiting for the app to report version $IMAGE_TAG..."
                        for i in $(seq 1 30); do
                            if curl -fsS "http://$IP/api/info" | grep -q "$IMAGE_TAG"; then
                                echo "SUCCESS: version $IMAGE_TAG is live at http://$IP"
                                exit 0
                            fi
                            sleep 10
                        done
                        echo "App did not report version $IMAGE_TAG in time"
                        kubectl get pods -o wide
                        exit 1
                    '''
                }
            }
        }
    }

    post {
        success { echo "Pipeline finished: commit ${env.IMAGE_TAG} is deployed." }
        failure { echo 'Pipeline failed - open the red stage and read its log.' }
        always  { sh 'docker image prune -f || true' }       // free disk on the Jenkins VM
    }
}
