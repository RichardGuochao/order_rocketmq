# Deployment and Test Plan — Order Processing System

**Date:** 2026-04-13
**Project:** `order-processing-system/`
**Tech Stack:** Spring Boot 3.2, MySQL 8, RocketMQ 5.x, Helm 3, Kubernetes

---

## Prerequisites

Verify the following before starting:

```bash
# Check all required tools
kubectl version --client          # Kubernetes client
helm version                      # Helm 3
mvn -version                       # Maven 3.9+
docker --version                   # Docker (for image build)
kubectl get nodes                  # Confirm cluster accessible
```

---

## Phase 1: Database Initialization

### 1.1 Apply Schema to MySQL

```bash
# Option A: Exec directly into MySQL pod (adjust pod name)
kubectl -n database get pods
kubectl exec -it <mysql-pod-name> -n database -- mysql -u root -p \
  < /Users/guochao/IdeaProjects/order_rabbitmq/order-processing-system/scripts/init.sql

# Option B: Port-forward and run locally
kubectl port-forward svc/mysql 3306:3306 -n database
mysql -h 127.0.0.1 -u root -p < scripts/init.sql
```

### 1.2 Verify Tables Created

```sql
USE order_db;
SHOW TABLES;
-- Expected: orders, order_outbox, order_event_log
DESCRIBE orders;
DESCRIBE order_outbox;
DESCRIBE order_event_log;
```

---

## Phase 2: Build and Package

### 2.1 Build JAR

```bash
cd /Users/guochao/IdeaProjects/order_rabbitmq/order-processing-system

# Clean compile (no tests during build)
mvn clean package -DskipTests

# Verify JAR created
ls -la target/*.jar
```

### 2.2 Build Docker Image

```bash
# Create minimal Dockerfile if not present
cat > Dockerfile << 'EOF'
FROM eclipse-temurin:17-jre-alpine
COPY target/order-processing-system-1.0.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
EOF

# Build and tag image
docker build -t order-processing:1.0.0 .

# Tag for local registry (adjust registry if needed)
docker tag order-processing:1.0.0 localhost:5000/order-processing:1.0.0
docker push localhost:5000/order-processing:1.0.0
```

---

## Phase 3: Helm Deployment

### 3.1 Deploy to Kubernetes

```bash
cd /Users/guochao/IdeaProjects/order_rabbitmq/order-processing-system

# Deploy with default values
helm install order-processing ./helm/order-processing \
  --namespace default \
  --create-namespace \
  --set secret.db.password=root

# Or with custom values file
helm install order-processing ./helm/order-processing \
  -f helm/order-processing/values.yaml \
  --namespace order-system \
  --create-namespace
```

### 3.2 Verify Deployment

```bash
# Check pods
kubectl get pods -n order-system -l app=order-processing

# Check logs
kubectl logs -n order-system -l app=order-processing --tail=50

# Check service
kubectl get svc -n order-system
```

### 3.3 Verify ConfigMap and Secret

```bash
kubectl get configmap -n order-system
kubectl get secret -n order-system
kubectl describe configmap order-processing-config -n order-system
```

---

## Phase 4: Functional Tests

### 4.1 Health Check

```bash
# Port-forward to access the service
kubectl port-forward svc/order-processing 8080 -n order-system &

# Test actuator health endpoint
curl -s http://localhost:8080/actuator/health | jq
# Expected: {"status":"UP"}
```

### 4.2 Create Order Test

```bash
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"orderNo": "ORD-TEST-001", "amount": 99.99}' | jq
```

**Expected Response (201):**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "orderNo": "ORD-TEST-001",
    "status": "CREATED",
    "amount": 99.99,
    "createdAt": "2026-04-13T...",
    "updatedAt": null
  }
}
```

### 4.3 Verify Order in MySQL

```bash
kubectl exec -it <mysql-pod> -n database -- mysql -u root -p -e \
  "SELECT * FROM order_db.orders WHERE order_no='ORD-TEST-001';"
```

**Expected:** Row with status `CREATED`

### 4.4 Verify Outbox Event Created

```bash
kubectl exec -it <mysql-pod> -n database -- mysql -u root -p -e \
  "SELECT id, aggregate_id, event_type, status, created_at FROM order_db.order_outbox WHERE aggregate_id='ORD-TEST-001';"
```

**Expected:** Row with `status=PENDING` and `event_type=ORDER_CREATED`

### 4.5 Update Order Status Test

```bash
curl -s -X PUT http://localhost:8080/api/v1/orders/ORD-TEST-001/status \
  -H "Content-Type: application/json" \
  -d '{"status": "PAID"}' | jq
```

**Expected Response (200):**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "orderNo": "ORD-TEST-001",
    "status": "PAID",
    "amount": 99.99,
    ...
  }
}
```

### 4.6 Verify Status Transition in MySQL

```bash
kubectl exec -it <mysql-pod> -n database -- mysql -u root -p -e \
  "SELECT order_no, status FROM order_db.orders WHERE order_no='ORD-TEST-001';"
# Expected: status = PAID
```

### 4.7 Verify Second Outbox Event

```bash
kubectl exec -it <mysql-pod> -n database -- mysql -u root -p -e \
  "SELECT aggregate_id, event_type, status FROM order_db.order_outbox WHERE aggregate_id='ORD-TEST-001';"
# Expected: 2 rows — ORDER_CREATED and ORDER_STATUS_CHANGED
```

### 4.8 Get Order Test

```bash
curl -s http://localhost:8080/api/v1/orders/ORD-TEST-001 | jq
```

### 4.9 List Orders Test

```bash
curl -s "http://localhost:8080/api/v1/orders?page=0&size=10" | jq
```

### 4.10 Invalid Status Transition Test (Negative Test)

```bash
# Try to transition from COMPLETED back to PAID (should fail)
curl -s -X PUT http://localhost:8080/api/v1/orders/ORD-TEST-001/status \
  -H "Content-Type: application/json" \
  -d '{"status": "PROCESSING"}' | jq
# First transition to PROCESSING
curl -s -X PUT http://localhost:8080/api/v1/orders/ORD-TEST-001/status \
  -H "Content-Type: application/json" \
  -d '{"status": "PROCESSING"}' | jq
curl -s -X PUT http://localhost:8080/api/v1/orders/ORD-TEST-001/status \
  -H "Content-Type: application/json" \
  -d '{"status": "SHIPPED"}' | jq
curl -s -X PUT http://localhost:8080/api/v1/orders/ORD-TEST-001/status \
  -H "Content-Type: application/json" \
  -d '{"status": "COMPLETED"}' | jq

# Now try invalid transition
curl -s -X PUT http://localhost:8080/api/v1/orders/ORD-TEST-001/status \
  -H "Content-Type: application/json" \
  -d '{"status": "PAID"}' | jq
```

**Expected:** HTTP 400 with error message about invalid status transition

---

## Phase 5: RocketMQ Integration Tests

### 5.1 Verify Messages Published to RocketMQ

Check broker logs or use RocketMQ console to verify `order-topic` has messages:

```bash
# If RocketMQ console is available
# Navigate to order-topic and check message count

# Or check application logs for successful send
kubectl logs -n order-system -l app=order-processing | grep -i "Transaction message sent"
```

### 5.2 Verify Consumer Processes Events

```bash
# Check consumer logs
kubectl logs -n order-system -l app=order-processing | grep -i "Event processed"

# Check order_event_log table for processed events
kubectl exec -it <mysql-pod> -n database -- mysql -u root -p -e \
  "SELECT * FROM order_db.order_event_log WHERE order_no='ORD-TEST-001';"
```

**Expected:** Entries in `order_event_log` for both `ORDER_CREATED` and `ORDER_STATUS_CHANGED`

### 5.3 Consumer Idempotency Test

```bash
# Manually replay a processed message by re-publishing to RocketMQ
# The consumer should detect the duplicate via event_id and skip processing

# Verify via logs
kubectl logs -n order-system -l app=order-processing | grep -i "Duplicate event detected"
```

---

## Phase 6: Unit Tests (Local)

### 6.1 Run Unit Tests

```bash
cd /Users/guochao/IdeaProjects/order_rabbitmq/order-processing-system
mvn test
```

**Expected:** All tests pass with BUILD SUCCESS

### 6.2 Expected Test Coverage

| Test | What It Verifies |
|---|---|
| `OrderServiceTest.createOrder_shouldPersistOrderAndOutbox` | Order and outbox row created in same transaction |
| `OrderServiceTest.createOrder_shouldThrowConflict_whenOrderExists` | Duplicate orderNo returns 409 |
| `OrderServiceTest.updateOrderStatus_shouldTransitionCorrectly` | Status updates when transition is valid |
| `OrderServiceTest.updateOrderStatus_shouldRejectInvalidTransition` | Invalid transition returns 400 |
| `OrderServiceTest.getOrder_shouldReturnOrder_whenExists` | Get order returns correct data |
| `OrderServiceTest.getOrder_shouldThrowNotFound_whenNotExists` | Get non-existent order returns 404 |
| `OrderEventConsumerTest.onMessage_shouldProcessOrderCreatedEvent` | ORDER_CREATED event is processed and logged |
| `OrderEventConsumerTest.onMessage_shouldSkipDuplicateEvent` | Duplicate event_id is skipped |
| `OrderEventConsumerTest.onMessage_shouldProcessStatusChangedEvent` | ORDER_STATUS_CHANGED event is processed |

---

## Phase 7: Helm Upgrade / Rollback

### 7.1 Helm Upgrade (apply config changes)

```bash
helm upgrade order-processing ./helm/order-processing \
  --namespace order-system \
  --set replicaCount=3
```

### 7.2 Helm Rollback

```bash
# Check revision history
helm history order-processing -n order-system

# Rollback to previous revision
helm rollback order-processing -n order-system
```

### 7.3 Helm Uninstall

```bash
helm uninstall order-processing -n order-system
kubectl delete pvc -n order-system -l app=order-processing  # cleanup PVCs
```

---

## Phase 8: End-to-End Flow Summary

| Step | Action | Verification |
|---|---|---|
| 1 | Deploy Helm | `kubectl get pods` shows running pods |
| 2 | POST /api/v1/orders | Response 201, MySQL has row with CREATED |
| 3 | MySQL outbox | Row inserted with PENDING |
| 4 | RocketMQ | Message published to order-topic |
| 5 | PUT /api/v1/orders/{no}/status | Response 200, MySQL updated |
| 6 | Consumer | order_event_log populated |
| 7 | Run unit tests | `mvn test` passes |
| 8 | Invalid transition | 400 error returned |

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---|---|---|
| Pods not starting | MySQL/RocketMQ unreachable | Check DNS, namespace, credentials |
| 500 on create order | MySQL connection | Verify DB URL in ConfigMap |
| Messages not in RocketMQ | Broker unreachable | Check rocketmq.name-server env var |
| Consumer not processing | Consumer group misconfig | Verify consumerGroup matches |
| Outbox stuck at PENDING | Broker ack failed | Check OutboxRetryScheduler logs |
| Optimistic lock error | Concurrent update | Retry operation |
