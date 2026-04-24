# Infrastructure Architecture

**Date:** 2026-04-24
**Cluster:** Kubernetes (desktop-control-plane)
**Namespace:** multi-namespace deployment

---

## Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Kubernetes Cluster                                 │
│                         (desktop-control-plane)                              │
│                                                                              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────────────────────┐  │
│  │   database   │    │  middleware  │    │       order-system            │  │
│  │   namespace  │    │   namespace  │    │        namespace             │  │
│  │              │    │              │    │                              │  │
│  │  ┌────────┐  │    │  ┌────────┐  │    │  ┌────────────────────────┐  │  │
│  │  │  MySQL │  │    │  │ NameSrv│  │    │  │   order-processing     │  │  │
│  │  │  3306  │  │    │  │  9876  │  │    │  │  ┌─────┐  ┌─────┐    │  │  │
│  │  └────────┘  │    │  └────────┘  │    │  │  │ Pod │  │ Pod │    │  │  │
│  │              │    │              │    │  │  │  1  │  │  2  │    │  │  │
│  │              │    │  ┌────────┐  │    │  │  └─────┘  └─────┘    │  │  │
│  │              │    │  │Broker │  │    │  │      Deployment      │  │  │
│  │              │    │  │10911  │  │    │  └────────────────────────┘  │  │
│  │              │    │  │10909  │  │    │            │                  │  │
│  │              │    │  │10912  │  │    │            ▼                  │  │
│  │              │    │  └────────┘  │    │     ┌─────────────┐         │  │
│  │              │    │              │    │     │  ClusterIP  │         │  │
│  │              │    │  ┌────────┐  │    │     │   :8080     │         │  │
│  │              │    │  │Dashboard│  │    │     └─────────────┘         │  │
│  │              │    │  │  8080  │  │    │                              │  │
│  │              │    │  └────────┘  │    │                              │  │
│  └──────────────┘    └──────────────┘    └──────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Namespaces

| Namespace       | Purpose                              | Age    |
|----------------|--------------------------------------|--------|
| `database`     | MySQL database                       | 11d    |
| `middleware`   | RocketMQ (broker, namesrv, dashboard) | 11d    |
| `order-system` | Order Processing System application   | 2d3h   |
| `istio-system` | Service mesh (if enabled)            | 11d    |
| `kube-system`  | Kubernetes system components         | 11d    |

---

## Services

### database namespace

| Service           | Type      | Cluster-IP    | Port(s)  | Endpoint                          |
|-------------------|-----------|---------------|----------|-----------------------------------|
| `mysql`           | ClusterIP | 10.96.75.81   | 3306/TCP | mysql.database.svc.cluster.local  |
| `mysql-headless`  | ClusterIP | None          | 3306/TCP | For StatefulSet DNS               |

### middleware namespace

| Service              | Type      | Cluster-IP    | Port(s)                  | Endpoint                                      |
|----------------------|-----------|---------------|--------------------------|-----------------------------------------------|
| `rocketmq-namesrv`   | ClusterIP | 10.96.34.50   | 9876/TCP                 | rocketmq-namesrv.middleware.svc.cluster.local |
| `rocketmq-broker`    | ClusterIP | 10.96.105.100 | 10911/TCP, 10909/TCP, 10912/TCP | rocketmq-broker.middleware.svc.cluster.local |
| `rocketmq-dashboard` | ClusterIP | 10.96.42.12   | 8080/TCP                 | rocketmq-dashboard.middleware.svc.cluster.local |

### order-system namespace

| Service           | Type      | Cluster-IP      | Port(s) | Endpoint                            |
|-------------------|-----------|-----------------|---------|-------------------------------------|
| `order-processing` | ClusterIP | 10.96.177.167   | 8080/TCP | order-processing.order-system.svc.cluster.local |

---

## Pods

### database namespace

| Pod       | Ready    | Status   | Restarts | Node                 | IP           |
|-----------|----------|----------|----------|---------------------|---------------|
| `mysql-0` | 1/1      | Running  | 12       | desktop-control-plane | 10.244.0.49  |

### middleware namespace

| Pod                   | Ready    | Status   | Restarts | Node                 | IP           |
|-----------------------|----------|----------|----------|---------------------|---------------|
| `rocketmq-namesrv-0` | 1/1      | Running  | 9        | desktop-control-plane | 10.244.0.x   |
| `rocketmq-broker-0`   | 1/1      | Running  | 9        | desktop-control-plane | 10.244.0.x   |
| `rocketmq-dashboard-xxx` | 1/1   | Running  | 1        | desktop-control-plane | 10.244.0.x   |

### order-system namespace

| Pod                                | Ready    | Status   | Restarts | Node                 | IP           |
|------------------------------------|----------|----------|----------|---------------------|---------------|
| `order-processing-766f7f4988-c7r9f` | 1/1      | Running  | 0        | desktop-control-plane | 10.244.0.72  |
| `order-processing-766f7f4988-lnt6h` | 1/1      | Running  | 0        | desktop-control-plane | 10.244.0.71  |

---

## Network Communication

```
Client (port-forward or Ingress)
        │
        ▼
order-processing:8080 (ClusterIP)
        │
        ├──────────────────┬────────────────────┐
        │                  │                    │
        ▼                  ▼                    ▼
   RocketMQ            MySQL              Other Services
   broker:10911      mysql:3306
   namesrv:9876
```

### Internal DNS Resolution

| Service                    | FQDN                                              |
|----------------------------|---------------------------------------------------|
| MySQL                      | `mysql.database.svc.cluster.local`                |
| RocketMQ NameServer        | `rocketmq-namesrv.middleware.svc.cluster.local`   |
| RocketMQ Broker           | `rocketmq-broker.middleware.svc.cluster.local`    |
| RocketMQ Dashboard        | `rocketmq-dashboard.middleware.svc.cluster.local` |
| Order Processing Service  | `order-processing.order-system.svc.cluster.local` |

---

## Configuration

### MySQL

- **Database:** `order`
- **User:** `root`
- **Port:** `3306`

### RocketMQ

- **NameServer:** `rocketmq-namesrv.middleware.svc.cluster.local:9876`
- **Broker:** `rocketmq-broker.middleware.svc.cluster.local:10911`
- **Topic:** `order-topic`

### Order Processing Application

- **Replicas:** 2
- **Image:** `guochaorichard/order-processing:1.0.0.20260423`
- **Port:** `8080`

---

## Deployment Commands

### Port-forward to access services locally

```bash
# MySQL
kubectl port-forward svc/mysql 3306 -n database

# RocketMQ Dashboard
kubectl port-forward svc/rocketmq-dashboard 8080 -n middleware

# Order Processing API
kubectl port-forward svc/order-processing 8080 -n order-system
```

### Check service status

```bash
# All pods
kubectl get pods -A | grep -E 'mysql|rocket|order'

# Specific namespace
kubectl get pods -n database
kubectl get pods -n middleware
kubectl get pods -n order-system

# Service endpoints
kubectl get endpoints -n database
kubectl get endpoints -n middleware
```

### View logs

```bash
# Order processing logs
kubectl logs -n order-system -l app=order-processing --tail=100

# MySQL logs
kubectl logs -n database mysql-0 --tail=100

# RocketMQ broker logs
kubectl logs -n middleware -l app.kubernetes.io/component=broker --tail=100
```

---

## Database Schema

### order database

| Table            | Purpose                                    |
|------------------|--------------------------------------------|
| `orders`         | Order master data                          |
| `order_outbox`   | Transactional outbox for reliable messaging |
| `order_event_log` | Consumer idempotency log                   |

---

## Environment Variables (Application)

| Variable                     | Default Value                                      |
|------------------------------|----------------------------------------------------|
| `DB_HOST`                    | `mysql.database.svc.cluster.local`                 |
| `DB_PORT`                    | `3306`                                             |
| `DB_NAME`                    | `order`                                            |
| `DB_USERNAME`                | `root`                                             |
| `DB_PASSWORD`                | `root`                                             |
| `ROCKETMQ_NAMESERVER`        | `rocketmq-namesrv.middleware.svc.cluster.local:9876` |
| `ROCKETMQ_PRODUCER_GROUP`    | `order-producer-group`                             |
| `ROCKETMQ_CONSUMER_GROUP`    | `order-consumer-group`                             |
| `ROCKETMQ_TOPIC_ORDER_EVENTS`| `order-topic`                                       |
| `SERVER_PORT`                | `8080`                                             |

---

## Architecture Notes

1. **ClusterIP Services** - All services use ClusterIP for internal communication within the cluster
2. **No Ingress** - External access via port-forward for development
3. **StatefulSet** - MySQL and RocketMQ components use StatefulSet for stable network identities
4. **Deployment** - Order processing uses Deployment with 2 replicas for HA
