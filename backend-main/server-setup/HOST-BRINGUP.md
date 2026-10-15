# Standing up an FSHRMS host

Bring one VM from bare Ubuntu 24.04 to a running environment. Both VMs use this
same procedure — `ENV_NAME` in `.env` is what makes one UAT and the other Prod.
Nothing is shared between them: separate Postgres, Redis, Kafka, MinIO,
registry, Jenkins, k3s and observability stack per host.

| | UAT | Prod |
|---|---|---|
| Host | `10.10.100.50` | `10.10.100.51` |
| Login | `hrm` | `hrms` |
| `ENV_NAME` | `uat` | `prod` |
| Hostnames | `*.uat.hrms.local` | `*.hrms.forward.pk` |

Written against the run that stood up prod on 2026-09-05. Every gotcha in the
last section was hit for real during it.

---

## 0. Snapshot

VMware snapshot named `pre-rebuild` before anything else. It is the only
rollback that exists.

## 1. SSH in

From your laptop:

```bash
ssh-copy-id -i ~/.ssh/id_ed25519.pub <user>@<host-ip>
```

If that fails with `Permission denied (publickey,password)` — wrong password, or
the account name isn't what you think — install the key from the **VMware
console** instead. SSH gives the identical error for a bad username and a bad
password, so don't burn time guessing:

```bash
whoami                        # settles the account name
mkdir -p ~/.ssh && chmod 700 ~/.ssh
echo '<your public key>' >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

Then add a host block to your laptop's `~/.ssh/config`:

```
Host prod 10.10.100.51
    HostName 10.10.100.51
    User hrms
    IdentityFile ~/.ssh/id_ed25519
    IdentitiesOnly yes
    ServerAliveInterval 10
    ServerAliveCountMax 3
```

## 2. Give the host git access

Full detail in `README.md` → "Linking a host to GitHub". Short form, on the VM:

```bash
ssh-keygen -t ed25519 -C "fshrms-$(hostname)" -f ~/.ssh/fshrms_deploy -N ""
ssh-keyscan -t rsa,ecdsa,ed25519 github.com >> ~/.ssh/known_hosts
cat ~/.ssh/fshrms_deploy.pub          # paste into GitHub
```

Add it at **repo → Settings → Deploy keys**, *Allow write access* **unchecked**.
Then point git at it so plain `git pull` works:

```bash
cat > ~/.ssh/config <<'CFG'
Host github.com
    HostName github.com
    User git
    IdentityFile ~/.ssh/fshrms_deploy
    IdentitiesOnly yes
CFG
chmod 600 ~/.ssh/config
ssh -T git@github.com          # must say: Hi <org>/<repo>!
```

**Check that greeting.** `Hi <org>/<repo>!` is a deploy key. `Hi <username>!`
means it landed on the account's SSH keys instead — that grants write access to
every repo that account can reach, from a server that runs unattended builds.

```bash
git clone git@github.com:fs-hrms/backend.git ~/Desktop/backend
```

## 3. Put `.env` on the host

`.env` is gitignored and never travels through git. From your laptop:

```bash
scp server-setup/.env.prod prod:~/Desktop/backend/server-setup/.env
ssh prod 'chmod 600 ~/Desktop/backend/server-setup/.env'
```

Leave `KAFKA_CLUSTER_ID` empty — `05` generates one and writes it back. It must
be unique per host; reusing another VM's ID against a fresh log dir crash-loops
the broker. Single-quote any password containing `$` or `#`.

## 4. Remove what conflicts

A VM handed over as "blank" often isn't. Check first:

```bash
pg_lsclusters                        # Postgres version and port
sudo -u postgres psql -l             # any real databases?
sudo ls -la /var/lib/jenkins/jobs    # any configured jobs?
kubectl get ns                       # any leftover namespaces?
docker ps -a && docker volume ls
```

Postgres must be **17** — `03` installs 17 from PGDG, and if an older cluster is
still online it keeps `:5432` and the new one silently lands on `:5433`:

```bash
sudo systemctl stop postgresql
sudo apt-get purge -y 'postgresql-16*' 'postgresql-client-16*'
sudo apt-get autoremove -y
sudo rm -rf /var/lib/postgresql/16 /etc/postgresql/16
pg_lsclusters                        # expect: no clusters
```

Keep `postgresql-common` — `03` needs it. If Jenkins is already installed and
has no jobs worth keeping, wipe its state so you don't end up with a
half-managed controller (`07` overwrites `casc.yaml` but leaves old jobs and
plugins):

```bash
sudo systemctl stop jenkins && sudo rm -rf /var/lib/jenkins/*
```

## 5. Install what's missing

`06` installs `kubeseal` itself. Maven is **not** needed — the wrapper is
`distributionType=only-script`, so `./mvnw` fetches Maven on first build. A
**JDK 21 is** needed: the wrapper fetches Maven but not a JDK, and the Jenkins
`.deb` only pulls in JDK 17, so the `build jar` stage fails "release version 21
not supported". `casc.yaml` points builds at `/usr/lib/jvm/java-21-openjdk-amd64`
via `JAVA_HOME`.

```bash
sudo apt-get install -y openjdk-21-jdk
curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
curl -fsSL "https://raw.githubusercontent.com/kubernetes-sigs/kustomize/master/hack/install_kustomize.sh" | bash
sudo install -m 755 kustomize /usr/local/bin/kustomize && rm kustomize
```

## 6. Run the scripts

Only these exist: `00`, `03`, `05`, `06`, `07`, plus `smoke.sh`. The `01`/`02`/
`04`/`08`/`09` in `docs/server-setup.md` were never written; Docker and k3s were
already installed on both VMs, which is what they would have done.

There is no passwordless sudo, and `06` runs long enough for the sudo timestamp
to expire mid-run — prime it each time:

```bash
cd ~/Desktop/backend/server-setup
sudo -v && ./scripts/00-os-update.sh        # apt full-upgrade, reboots if needed
# ── re-SSH after reboot ──
sudo -v && ./scripts/03-postgres-host-install.sh
sudo -v && ./scripts/05-infrastructure-up.sh
sudo -v && ./scripts/06-platform-up.sh      # slowest: several minutes of helm --wait
sudo -v && ./scripts/07-jenkins-install.sh
```

Snapshot between phases: `pg-up`, `infra-up`, `platform-up`.

Verify as you go:

```bash
# after 03 — the real proof is a TCP login as the env role, not the script output
set -a && . ./.env && set +a
PGPASSWORD="$PG_PASSWORD" psql -h 127.0.0.1 -U "$ENV_NAME" -d "$ENV_NAME" \
    -At -c "SELECT current_database(), current_user;"

# after 05 — confirm a '$' in the password survived compose interpolation
docker exec redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning ping   # PONG

# after 06 — all five must say `deployed`, not `pending-install`
helm ls -A
ls -l k8s/platform/sealed-secrets-$ENV_NAME.pub.pem
```

`sealed-secrets-<env>.pub.pem` is that cluster's **only** sealing identity.
Commit it. Without it nothing can produce sealed secrets for this environment
from anywhere else.

## 7. Jenkins deploy credentials

`07` refuses to run until both files exist, because JCasC turns a missing file
into an empty credential rather than an error.

```bash
sudo install -d -m 750 -o root -g jenkins /etc/jenkins/credentials
sudo install -m 640 -o root -g jenkins \
    ~/.ssh/fshrms_deploy /etc/jenkins/credentials/github_id_ed25519
```

The kubeconfig comes from a namespace-scoped service account. `edit` does not
cover the sealed-secrets or Prometheus CRDs, hence the two extra Roles:

```bash
kubectl apply -f - <<'YAML'
apiVersion: v1
kind: ServiceAccount
metadata: { name: jenkins-deployer, namespace: prod }
---
# k8s 1.24+ stopped auto-creating SA token secrets; a CI deployer wants a
# long-lived one rather than a 1h projected token.
apiVersion: v1
kind: Secret
metadata:
  name: jenkins-deployer-token
  namespace: prod
  annotations: { kubernetes.io/service-account.name: jenkins-deployer }
type: kubernetes.io/service-account-token
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata: { name: jenkins-deployer-edit, namespace: prod }
roleRef: { apiGroup: rbac.authorization.k8s.io, kind: ClusterRole, name: edit }
subjects: [{ kind: ServiceAccount, name: jenkins-deployer, namespace: prod }]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata: { name: jenkins-deployer-sealedsecrets, namespace: prod }
rules: [{ apiGroups: ["bitnami.com"], resources: ["sealedsecrets"], verbs: ["*"] }]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata: { name: jenkins-deployer-sealedsecrets, namespace: prod }
roleRef: { apiGroup: rbac.authorization.k8s.io, kind: Role, name: jenkins-deployer-sealedsecrets }
subjects: [{ kind: ServiceAccount, name: jenkins-deployer, namespace: prod }]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata: { name: jenkins-deployer-monitoring, namespace: prod }
rules: [{ apiGroups: ["monitoring.coreos.com"], resources: ["servicemonitors","prometheusrules","podmonitors"], verbs: ["*"] }]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata: { name: jenkins-deployer-monitoring, namespace: prod }
roleRef: { apiGroup: rbac.authorization.k8s.io, kind: Role, name: jenkins-deployer-monitoring }
subjects: [{ kind: ServiceAccount, name: jenkins-deployer, namespace: prod }]
YAML

CA=$(kubectl -n prod get secret jenkins-deployer-token -o jsonpath='{.data.ca\.crt}')
TOKEN=$(kubectl -n prod get secret jenkins-deployer-token -o jsonpath='{.data.token}' | base64 -d)
cat > /tmp/kubeconfig-prod <<EOF
apiVersion: v1
kind: Config
clusters:
- name: k3s
  cluster: { server: https://127.0.0.1:6443, certificate-authority-data: $CA }
users:
- name: jenkins-deployer
  user: { token: $TOKEN }
contexts:
- name: prod
  context: { cluster: k3s, user: jenkins-deployer, namespace: prod }
current-context: prod
EOF

kubectl --kubeconfig=/tmp/kubeconfig-prod -n prod get deploy    # must not say Forbidden
sudo install -m 640 -o root -g jenkins /tmp/kubeconfig-prod /etc/jenkins/credentials/kubeconfig
shred -u /tmp/kubeconfig-prod
```

`127.0.0.1:6443` is correct — Jenkins runs on the same host as k3s.

## 8. Public entry (cPanel reverse proxy)

The office connection does not expose 80 or 443, so TLS terminates on the
company cPanel host (`65.98.127.106`) and traffic is proxied inward over a
forwarded high port. Prod uses **5000**. This is the same pattern already
serving `sialkotfootballacademy.pk`.

1. **DNS.** `hrms.forward.pk` must resolve to `65.98.127.106`, not to the
   office. It currently points at `125.209.66.227`; leave it there and AutoSSL
   can never validate, because cPanel is not what answers.
2. **cPanel → Domains → Create A New Domain:** `hrms.forward.pk`, doc root
   `/hrms.forward.pk`. Let AutoSSL issue before testing the proxy.
3. **`.htaccess`** in that doc root:

   ```apache
   RewriteEngine on

   # Let AutoSSL answer its own ACME challenge. MUST come before the proxy
   # rule — otherwise the catch-all forwards /.well-known/ into the cluster,
   # it 404s, and the certificate stops renewing about 60 days later.
   RewriteRule ^\.well-known/ - [L]

   RewriteCond %{HTTPS} off
   RewriteRule ^(.*)$ https://%{HTTP_HOST}/$1 [R=301,L]

   RewriteRule ^(.*)$ http://125.209.66.227:5000/$1 [P,L]
   ```

   Use `[P]`, not a cPanel "Redirects To" entry — a 301 leaks `IP:port` into
   the address bar, which is what `hr/labtest/souvenir.forward.pk` do.
4. **Router:** forward `5000` → `10.10.100.51:80`. Port **80**, not 443 —
   there is no certificate in the cluster. Restrict the rule to source
   `65.98.127.106`; the hop is plaintext over the public internet and this
   one carries payroll data.
5. **Verify from outside the LAN**, with a known-open port as the control:

   ```bash
   curl -sv --max-time 8 http://125.209.66.227:4400/   # control: connects
   curl -sv --max-time 8 http://125.209.66.227:5000/   # must connect too
   ```

   Read the failure mode, it localises the fault:

   | Result | Means |
   |---|---|
   | `Connected` | NAT is right; a 404 here is just Traefik with no `web` pod yet |
   | `Connection refused` | Reached a device that sent RST — no forward for that port |
   | Silent timeout | Packets dropped — forwarded to a host that is blackholing them, usually `ufw` on the destination, or an explicit DROP |

   On the destination VM: `sudo ufw status verbose` and
   `sudo ss -lntp | grep ':80 '` (expect the k3s svclb/Traefik listener).

---

---

## Gotchas

Every one of these was hit standing up prod. Symptom first, because that's what
you'll have.

### `syntax error at or near ":"` at the end of `03`

`psql` interpolates `:'var'` only on stdin/file input, **never** in a `-c`
string. Cosmetic when it happens in a status query, silent corruption if you
ever use `-c` for real work. Use a heredoc.

### `helm repo add` 404 on sealed-secrets

The project moved `bitnami-labs/sealed-secrets` → `bitnami/sealed-secrets` and
the old gh-pages repo was withdrawn. Current URL is
`https://bitnami.github.io/sealed-secrets`. Upstream renames like this will keep
happening; the chart index returning 404 rather than a redirect is the tell.

### `kubectl` says permission denied on `/etc/rancher/k3s/k3s.yaml`

Two problems stacked, and fixing one looks like fixing nothing:

1. k3s writes that file `root:root 0600`.
2. k3s installs `kubectl` as a **symlink to the k3s binary**, and that wrapper
   defaults to `/etc/rancher/k3s/k3s.yaml` when `KUBECONFIG` is unset — it does
   *not* fall back to `~/.kube/config` the way upstream kubectl does.

So copying the file to `~/.kube/config` changes nothing on its own; you must
also export `KUBECONFIG`. `06` now does both and appends the export to
`~/.bashrc`. Don't reach for `--write-kubeconfig-mode 644`: that makes
cluster-admin credentials readable by every account on a host that runs Jenkins.

### `Load key "...": error in libcrypto` / `Permission denied (publickey)` in a Jenkins job

The credential holds an **empty** key. JCasC's `${readFile:...}` and
`${readFileBase64:...}` expand to nothing for a missing file and log no warning
— the same class as the `${base64File:...}` typo that produced an empty
kubeconfig and sent kubectl to `localhost:8080`. `07` now fails up front if the
staged files are missing or empty. Never nest interpolations
(`${readFileBase64:...${ENV_NAME}}`); put the variable in the credential *id*
and keep the path fixed.

### `helm ls` doesn't show a release you know exists

It hides anything not `deployed`. Use `helm ls -A --all` to see
`pending-install`. And `pending-install` mid-run is **normal** — it only means
trouble if no helm process is alive:

```bash
pgrep -af "06-platform-up|helm (upgrade|install)"
```

If the release is genuinely orphaned, `helm -n <ns> uninstall <release>` before
re-running, or the next install refuses with "another operation is in progress".

### `docker compose config` shows the password as `pass$$`

That's compose escaping `$` on output so the YAML round-trips; the container
receives a single `$`. Verify against the running service rather than trusting
either rendering:

```bash
docker exec redis redis-cli -a 'literal-password' --no-auth-warning ping
```

### `permission denied ... unix:///var/run/docker.sock` in `docker build + push`

The `jenkins` user isn't in the `docker` group. `07` now adds it before
starting Jenkins, but group membership only lands on a fresh process — if
Jenkins is already up, `sudo usermod -aG docker jenkins && sudo systemctl
restart jenkins`.

### `release version 21 not supported` in the `build jar` stage

`./mvnw` runs on the Jenkins built-in node under that host's `java`, and the
Jenkins `.deb` only pulls in JDK 17 — `javac 17` rejects `--release 21`. The
first two reactor modules are pom-packaged so they "pass"; `commons-core` is the
first with real sources and takes the failure. Fix: `apt-get install
openjdk-21-jdk` (§5) — `casc.yaml` already sets `JAVA_HOME` to it for builds
without disturbing the JVM Jenkins core runs on.

### sudo re-prompts in the middle of a script

The sudo timestamp expires during `06`'s helm waits. `sudo -v` immediately
before each script.

### Scripts referenced in the plan that don't exist

`docs/server-setup.md` lists `01`, `02`, `04`, `08`, `09`. They were never
written. Docker and k3s came pre-installed on both VMs.

---

## What this does not cover

Standing up the platform is not the same as running the application. Closed
since this doc was written (2026-09-12): every prod overlay now overrides
`DB_HOST` / `REDIS_HOST` / `KAFKA_BROKERS` / `MINIO_ENDPOINT` onto
`10.10.100.51` and points `newName` at this host's registry;
`attendance-poller` has a prod overlay; the public suffix moved to
`hrms.forward.pk` with ACME TLS; prod pipelines were pinned to the `prod`
branch.

Still required before prod serves traffic:

- **`employee-secret` is missing `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`.**
  `employee/src/main/resources/application.yml` has no default for either, so
  the context fails at startup. `chat` and `payroll` prod secrets carry them;
  employee's does not. Hard blocker.
- **`auth-secret` is missing `HRMS_AUTH_BOOTSTRAP_ADMIN_PASSWORD`**, so the
  bootstrap admin is created with the committed default `ChangeMe!2026`.
- **`attendance-poller-secret` is still an empty placeholder.** `POLLER_TOKEN`
  has to be issued against prod auth once auth is up, then resealed.
- **A real RS256 JWT keypair for auth.** Nothing in `services/auth/k8s/` sets
  `HRMS_AUTH_JWT_PRIVATE_KEY_LOCATION` / `..._PUBLIC_KEY_LOCATION`, so prod
  falls back to the dev keypair committed in `commons-web`. Anyone with the
  repo can mint prod tokens.
- **`HRMS_OPENAPI_SERVER_URL` is `http://api.hrms.forward.pk`** in all ten prod
  overlays, but the ingress redirects `:80` to HTTPS and `forward.pk` is HSTS.
  Swagger try-it-out breaks on the cross-scheme redirect.
- The frontend repo needs a `Jenkinsfile-prod` for the `prod/web` job.

Prod jobs take no parameters and build the head of the `prod` branch. Promote
by merging into `prod`; roll back by moving `prod` to the older commit and
re-running.
