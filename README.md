# cicd-demo — End-to-End Cloud CI/CD Pipeline (Jenkins)

Java (Spring Boot) web app → Jenkins pipeline → Maven build/test → Docker image → Azure Container Registry → Azure Kubernetes Service, deployed with Ansible.

```
git push ─► GitHub webhook ─► Jenkins ─► mvn package ─► mvn test ─► docker build/push (ACR) ─► Ansible ─► AKS ─► public IP
```

| Path | Purpose |
|---|---|
| `src/` | Application code and unit tests |
| `pom.xml` | Maven build definition |
| `Dockerfile`, `.dockerignore` | Multi-stage image build |
| `Jenkinsfile` | The pipeline (7 stages) |
| `k8s/` | Kubernetes Deployment + Service |
| `ansible/deploy.yml` | Deploys the app to Kubernetes |
| `ansible/setup-jenkins-server.yml` | Configures the Jenkins VM |

Run locally: `mvn spring-boot:run` → http://localhost:8080
Run tests: `mvn verify`
