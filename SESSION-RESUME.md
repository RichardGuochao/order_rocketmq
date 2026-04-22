# Session Resume — Order Processing System

## Project Location
`/Users/guochao/IdeaProjects/order_rabbitmq/order-processing-system/`

## Maven
Maven installed at `~/.apache-maven/`, added to `~/.zshrc`. Available as `mvn` in new terminal.

---

## Completed Steps

### ✅ Compilation & Tests
- `mvn clean compile` — BUILD SUCCESS
- `mvn test` — 9 tests passed (6 OrderService + 3 OrderEventConsumer)
- `mvn package -DskipTests` — JAR built at `target/order-processing-system-1.0.0-SNAPSHOT.jar` (113MB)

### ✅ MySQL Schema Initialized
- Database: `order` (from MySQL secret `mysql.database`)
- Tables: `orders`, `order_outbox`, `order_event_log`
- MySQL root password: `root`
- Apply schema command:
  ```bash
  cat scripts/init.sql | kubectl exec -i mysql-0 -n database -- mysql -uroot -proot
  ```

### ✅ Configuration Fixed
- `application.yml` — DB name `order`, RocketMQ configs use `${ENV_VAR}` placeholders
- `application-default.yml` — DB name `order`
- `helm/order-processing/values.yaml` — db.name: `order`
- `.env` — all config values template

---

## Blocked: Docker Image Build

### Error
```
failed to authorize: DeadlineExceeded: failed to fetch oauth token:
Post "https://auth.docker.io/token": dial tcp 108.160.165.53:443: i/o timeout
```

### Root Cause Analysis

**VPN + Proxy misconfiguration:**
1. Docker Desktop is configured with HTTP proxy: `http.docker.internal:3128`
2. This proxy is inside Docker Desktop's Linux VM network
3. Host shell uses DNS `223.5.5.5` (Alibaba DNS) which cannot route to Docker Hub IPs
4. `http.docker.internal` only resolves from inside Docker Desktop VM, not from host macOS
5. VPN is active on macOS but does not route shell traffic

**Network path confirmed broken:**
- `nslookup auth.docker.io` → resolves to `108.160.165.53` ✅
- `ping 108.160.165.53` → 100% packet loss ❌
- TCP port 443 to Docker Hub IPs → unreachable ❌
- `curl https://registry-1.docker.io/v2/` → timeout ❌

### Git Commits
```
a8fee32 docs: update session resume with actual deployment values and status
25b3e06 feat: update configuration for actual MySQL/RocketMQ deployment
01b0ffa fix: resolve compilation errors (setSendMsgTimeout, final vars)
e75d093 feat: add Dockerfile for container image build
7b5ef03 docs: add deployment and test plan
ec4763a test: add OrderServiceTest and OrderEventConsumerTest
aa6909a feat: add Helm chart for Kubernetes deployment
... (total ~17 commits)
```

---

## What's Needed to Complete Deployment

1. **Docker image** — needs registry/mirror accessible from Docker Desktop proxy, OR different approach
2. **Helm install** — `helm install order-processing ./helm/order-processing --namespace order-system`
3. **Functional tests** — per `docs/DEPLOYMENT-TEST-PLAN.md`

## Alternative Approaches to Explore

- Configure Docker Desktop proxy in System Settings to route through VPN-compatible proxy
- Use a VPN-compatible proxy server that Docker Desktop can also use
- Use a local registry mirror (e.g., `registry.cn-hangzhou.aliyuncs.com` mirrors Docker Hub)
- Build image in a VM/cloud environment with internet, push to registry, then deploy
- Use `kind` build with `make` instead of Docker Hub image
