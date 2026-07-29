---
title: Kubernetes Operator
description: Run the Flink 2.2 learning cluster through the production-aligned operator contract.
created: 2026-07-30 00:00
modified: 2026-07-30 00:00
type: concept
status: active
maturity: seed
tags:
    - apache-flink
    - kubernetes
    - operators
source: https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-release-1.15/docs/try-flink-kubernetes-operator/quick-start/
---

# Kubernetes Operator

The local Compose environment and the Kubernetes Operator answer different
questions:

- Podman Compose proves that the packaged job runs against Flink.
- The Kubernetes Operator reconciles a `FlinkDeployment` custom resource into
  a managed Flink cluster.

This project aligns the Kubernetes path with Java 17, Flink 2.2.1, and Flink
Kubernetes Operator 1.15.0. The initial manifest creates a session cluster so
you can inspect reconciliation before adding image-building and application-job
deployment concepts.

## Install the operator

Use a disposable Kubernetes cluster or a development namespace. Your current
`kubectl` context will be modified.

```sh
helm repo add flink-operator-repo \
  https://downloads.apache.org/flink/flink-kubernetes-operator-1.15.0/
helm install flink-kubernetes-operator \
  flink-operator-repo/flink-kubernetes-operator
```

## Reconcile the session cluster

```sh
kubectl apply -f environments/flink/kubernetes/flink-deployment.yaml
kubectl get flinkdeployments
kubectl describe flinkdeployment flos-session
```

The manifest uses the operator's `flink.apache.org/v1beta1` API and declares
`flinkVersion: v2_2`. A healthy result shows the deployment progressing to a
ready state and exposes the Flink session cluster managed by the operator.
