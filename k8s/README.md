# Kubernetes Deployment

## Voraussetzungen

1. **Namespace erstellen:**
   ```bash
   kubectl apply -f namespace.yaml
   ```

2. **Image Pull Secret (ExternalSecret):**
   - Secret wird automatisch aus Azure Key Vault (`treasurykeyvault.vault.azure.net`) gezogen
   - Secret Name: `imagePullSecret`
   - ClusterSecretStore: `azure-kv`
   - ExternalSecret: `regcred-external-secret` erstellt `regcred` Secret

## Deployment

```bash
kubectl apply -f namespace.yaml
kubectl apply -f regcred-external-secret.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
kubectl apply -f ingress.yaml
```

## Status prüfen

```bash
kubectl get pods -n magiccollection
kubectl get svc -n magiccollection
kubectl get ingress -n magiccollection
kubectl get externalsecrets -n magiccollection
kubectl logs -n magiccollection -l app=magic-collection
```

## Image manuell aktualisieren

```bash
kubectl set image deployment/magic-collection magic-collection=ghcr.io/biestervictor/mtg-springboot:<tag> -n magiccollection
```

## Löschen

```bash
kubectl delete -f ingress.yaml
kubectl delete -f service.yaml
kubectl delete -f deployment.yaml
kubectl delete -f regcred-external-secret.yaml
kubectl delete -f namespace.yaml
```

2. **Image Pull Secret erstellen:**
   ```bash
   # Token generieren (GitHub Settings > Developer Settings > Personal Access Tokens)
   # Benötigt: read:packages
   
   # Base64 encode:
   echo -n '{"auths":{"ghcr.io":{"username":"<github-user>","password":"<github-token>"}}}' | base64
   
   # In regcred.yaml einfügen und anwenden:
   kubectl apply -f regcred.yaml
   ```

## Deployment

```bash
kubectl apply -f namespace.yaml
kubectl apply -f regcred.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
kubectl apply -f ingress.yaml
```

## Status prüfen

```bash
kubectl get pods -n magiccollection
kubectl get svc -n magiccollection
kubectl get ingress -n magiccollection
kubectl logs -n magiccollection -l app=mtg-collection
```

## Image manuell aktualisieren

```bash
kubectl set image deployment/mtg-collection mtg-collection=ghcr.io/biestervictor/mtg-springboot:<tag> -n magiccollection
```

## Löschen

```bash
kubectl delete -f ingress.yaml
kubectl delete -f service.yaml
kubectl delete -f deployment.yaml
kubectl delete -f namespace.yaml
```
