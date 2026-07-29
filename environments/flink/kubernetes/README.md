# Flink Kubernetes Operator environment

This manifest targets Apache Flink Kubernetes Operator 1.15.0 and starts a
Flink 2.2.1 session cluster on Java 17.

Install the operator into the current Kubernetes context:

```sh
helm repo add flink-operator-repo \
  https://downloads.apache.org/flink/flink-kubernetes-operator-1.15.0/
helm install flink-kubernetes-operator \
  flink-operator-repo/flink-kubernetes-operator
```

Create the session cluster:

```sh
kubectl apply -f environments/flink/kubernetes/flink-deployment.yaml
kubectl get flinkdeployments
```

This is separate from the Podman Compose environment. Compose runs the Flink
runtime locally; Kubernetes is required to learn operator reconciliation.
