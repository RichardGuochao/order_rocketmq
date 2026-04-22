# Session Resume — Order Processing System

## Project Location
`/Users/guochao/IdeaProjects/order_rabbitmq/order-processing-system/`

## Maven
Maven installed at `~/.zshrc` (export PATH=$HOME/apache-maven/bin:$PATH). Available as `mvn` in new terminal.

## What Was Done

### Compilation & Tests — PASSED
- `mvn clean compile` — BUILD SUCCESS
- `mvn test` — 9 tests passed
- `mvn package -DskipTests` — JAR built at `target/order-processing-system-1.0.0-SNAPSHOT.jar` (113MB)

### MySQL Schema — INITIALIZED
- Database: `order` (not `order_db`)
- Tables created: `orders`, `order_outbox`, `order_event_log`
- MySQL root password: `root`
- Apply schema command (requires `-i` for stdin):
  ```bash
  cat scripts/init.sql | kubectl exec -i mysql-0 -n database -- mysql -uroot -proot
  ```

### Configuration Fixed
- `application.yml` — DB name `order`, all RocketMQ configs use `${ENV_VAR}` placeholders
- `application-default.yml` — DB name `order`
- `helm/order-processing/values.yaml` — db.name: `order`
- `.env` — all config values (not committed, contains secrets)

## What's Remaining

### 1. Docker Image Build (blocked by network)
Docker cannot pull `eclipse-temurin:17-jre-alpine` from Docker Hub — network timeout. Options:
- Fix Docker network/proxy to reach Docker Hub
- Use a local registry mirror
- Build in environment with internet access, push to registry, then deploy

```bash
docker build -t order-processing:1.0.0 .
docker tag order-processing:1.0.0 <your-registry>/order-processing:1.0.0
docker push <your-registry>/order-processing:1.0.0
```

Update `helm/order-processing/values.yaml` `image.repository` to match your registry.

### 2. Helm Deploy (after image is available)

```bash
helm install order-processing ./helm/order-processing \
  --namespace order-system \
  --create-namespace \
  --set secret.db.password=root
```

### 3. Functional Tests (after deploy)
```bash
kubectl port-forward svc/order-processing 8080 -n order-system &
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"orderNo": "ORD-TEST-001", "amount": 99.99}'
```

## Correct Config Values
```
DB_HOST=mysql.database.svc.cluster.local
DB_PORT=3306
DB_NAME=order
DB_USERNAME=root
DB_PASSWORD=root
ROCKETMQ_NAMESERVER=rocketmq.middleware.svc.cluster.local:9876
ROCKETMQ_PRODUCER_GROUP=order-producer-group
ROCKETMQ_CONSUMER_GROUP=order-consumer-group
ROCKETMQ_TOPIC_ORDER_EVENTS=order-topic
```

## Git Log (recent commits)
```
25b3e06 feat: update configuration for actual MySQL/RocketMQ deployment
01b0ffa fix: resolve compilation errors
e75d093 feat: add Dockerfile for container image build
7b5ef03 docs: add deployment and test plan
```
