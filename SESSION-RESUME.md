# Session Resume — Order Processing System

## Project Location
`/Users/guochao/IdeaProjects/order_rabbitmq/order-processing-system/`

## Maven
Maven installed at `~/.apache-maven/`, added to `~/.zshrc`. Available as `mvn` in new terminal.

---

## Current Deployment Status

### ✅ Application Deployed and Running
- **Image:** `guochaorichard/order-processing:1.0.0.20260424-v2` pushed to Docker Hub
- **Helm Release:** `order-processing` revision 2 deployed in namespace `default`
- **Pods:** 2/2 Running (Ready)
- **API:** Working at `http://localhost:8080/api/v1/orders` (via port-forward)

### ✅ Verified Functionality
| Endpoint | Method | Status |
|----------|--------|--------|
| `/api/v1/orders` | POST | Working - creates order |
| `/api/v1/orders/{orderNo}` | GET | Working - retrieves order |
| `/api/v1/orders/{orderNo}/status` | PUT | Working - updates status |
| `/actuator/health` | GET | Working - shows UP |

### ✅ RocketMQ Consumer Working
- Consumer connected to nameserver `rocketmq-namesrv.middleware.svc.cluster.local:9876`
- Messages consumed successfully
- Duplicate detection working (idempotent consumer via `order_event_log` table)

### ✅ Outbox Pattern Working
- Outbox retry scheduler successfully republished 6 pending outbox events
- All 6 outbox entries (1-6) marked as republished successfully
- Orders and outbox events stored in MySQL `order` database

---

## Fixed Issues

### Issue 1: RocketMQ NameServer Address (RESOLVED)
**Problem:** Nameserver address was incorrect in configuration

**Fix:** Changed `rocketmq.middleware.svc.cluster.local` to `rocketmq-namesrv.middleware.svc.cluster.local`
- Updated `helm/order-processing/values.yaml`
- Updated `src/main/resources/application.yml`
- Updated `src/main/resources/application-default.yml`

### Issue 2: Outbox Transaction Update Failed (RESOLVED)
**Problem:** `OutboxRetryScheduler` and `OrderOutboxPublisher.executeLocalTransaction` were calling `@Modifying` queries (`updateStatus()`) which require Spring's transaction context. These methods run outside Spring's proxy:
- `OutboxRetryScheduler` - scheduled task runs on scheduler thread
- `OrderOutboxPublisher.executeLocalTransaction` - called by RocketMQ thread

**Error:**
```
Executing an update/delete query
```

**Fix:** Changed from `@Modifying` JPQL update query to using `findById()` + `save()`:
```java
// Before (fails - no transaction context)
orderOutboxRepository.updateStatus(outbox.getId(), OrderOutbox.STATUS_PUBLISHED, LocalDateTime.now());

// After (works - save() handles entity merge)
OrderOutbox toUpdate = orderOutboxRepository.findById(outbox.getId()).orElse(null);
if (toUpdate != null) {
    toUpdate.setStatus(OrderOutbox.STATUS_PUBLISHED);
    toUpdate.setPublishedAt(LocalDateTime.now());
    orderOutboxRepository.save(toUpdate);
}
```

**Files Modified:**
- `src/main/java/com/example/order/service/OrderOutboxPublisher.java`
- `src/main/java/com/example/order/service/OutboxRetryScheduler.java`

### Issue 3: Docker Proxy (RESOLVED)
Proxy was disabled in Docker Desktop - pushes now working.

---

## Git Status
All code changes committed to `order-processing-system/` subdirectory.

---

## Configuration Files Updated
- `Dockerfile` - Multi-stage build with correct JAR path (`/app/app.jar`)
- `Order.java` - Added `columnDefinition = "VARCHAR(32)"` to fix Hibernate validation
- `application.yml` - `auto-start-consumer: false` removed, consumer enabled
- `OrderEventConsumer.java` - Consumer enabled (no `@Profile` exclusion)
- `RocketMQConfig.java` - Uses `setSendMsgTimeout` (not `setSendTimeout`)
- `OrderService.java` - Fixed inner class variable capture issue
- `helm/order-processing/values.yaml` - Image `guochaorichard/order-processing:1.0.0.20260424-v2`
- `OrderOutboxPublisher.java` - Uses `save()` instead of `@Modifying` query
- `OutboxRetryScheduler.java` - Uses `save()` instead of `@Modifying` query

---

## Quick Commands Reference

```bash
# Check pods
kubectl get pods -l app=order-processing

# View logs
kubectl logs -l app=order-processing --tail=50

# Port-forward for API access
kubectl port-forward svc/order-processing 8080:8080

# Test API
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"TEST-001","amount":50.00}'

# Check outbox events (via MySQL client pod)
kubectl run mysql-client --image=mysql:8 --rm -it --restart=Never -- \
  mysql -h mysql.database.svc.cluster.local -uroot -proot -e "USE \`order\`; SELECT id, status, event_type, created_at FROM order_outbox ORDER BY id DESC LIMIT 5;"

# Check RocketMQ nameserver logs
kubectl logs -l app=rocketmq-namesrv -n middleware --tail=20
```
