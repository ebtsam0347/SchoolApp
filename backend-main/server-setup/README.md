# server-setup

Reproducible build of one FSHRMS VM.

One environment per host: `ENV_NAME=uat` on VM1 (10.10.100.50), `ENV_NAME=prod`
on VM2 (10.10.100.51). The same scripts build both; only `.env` differs.

The full plan lives in `fshrms/docs/server-setup.md`. This repo is the
executable form of that plan: `scripts/` runs phase-by-phase against a fresh
Ubuntu 24.04 VM and lands at the same state every time.

**Standing up a host from scratch: `HOST-BRINGUP.md`.** Step-by-step, plus every
gotcha hit doing it for real.

## Layout

```
server-setup/
├── README.md
├── .env.example          (copy to .env, fill in, never commit)
├── .env.prod             (VM2's values; gitignored — `cp .env.prod .env` there)
├── .gitignore
├── infrastructure/       (host docker compose: redis, kafka, minio, registry, UIs)
├── jenkins/              (JCasC + pinned plugins)
├── k8s/                  (namespaces, platform manifests, ingress)
└── scripts/
    ├── 00-os-update.sh                Phase 0
    ├── 03-postgres-host-install.sh    Phase 2
    ├── 05-infrastructure-up.sh        Phase 3
    ├── 06-platform-up.sh              Phase 4
    └── 07-jenkins-install.sh          Phase 6
```

`k8s/namespaces.yaml` and the ingress manifests are templates —
`06-platform-up.sh` substitutes `ENV_NAME_PLACEHOLDER` and
`HOST_SUFFIX_PLACEHOLDER` before applying. Prod serves on the real domain
(`app.hrms.forward.pk`); every other environment stays on the `.local` LAN
convention, subdomained under its own name (`app.uat.hrms.local`). Set
`HOST_SUFFIX` in `.env` to override the default for a host.

## Running

```bash
cp .env.example .env            # on VM2: cp .env.prod .env
$EDITOR .env                    # set ENV_NAME, HOST_IP, and the passwords
./scripts/00-os-update.sh       # idempotent; reboots if needed
./scripts/03-postgres-host-install.sh
./scripts/05-infrastructure-up.sh
./scripts/06-platform-up.sh
./scripts/07-jenkins-install.sh
```

`KAFKA_CLUSTER_ID` is generated on first run of `05-infrastructure-up.sh` and
written back to `.env`. It must be unique per host — never copy VM1's value to
VM2; a reused KRaft cluster ID against a fresh log dir crash-loops the broker.

Each script is idempotent. Re-running it on a configured machine should be
a no-op (or update in place — passwords get re-applied via `ALTER ROLE`,
config files are overwritten).

## Linking a host to GitHub

Jenkins clones over SSH with a **read-only deploy key**. Each VM gets its own
key: a compromised prod host shouldn't hand over UAT's access, and revoking one
host doesn't lock out the other. Nothing here needs write access to the repo —
pipelines only read.

The operator's own clone of this repo (the one you run `scripts/` from) is
separate and can use HTTPS. The chicken-and-egg is real: you need the repo on
the box before `07-jenkins-install.sh` can configure Jenkins to clone it.

### 1. Get the repo onto the VM

```bash
# HTTPS, using your own GitHub account. Fine for a private repo — it prompts
# once for a personal access token.
git clone https://github.com/fs-hrms/backend.git ~/fshrms
cd ~/fshrms/server-setup
cp .env.prod .env          # on VM2; on VM1 use .env.example as the base
```

### 2. Generate the host's deploy key

Generate it **as the operator**, not as `jenkins` — the file is staged into
`/etc/jenkins/credentials/` and read by JCasC at startup, so Jenkins never
needs `~/.ssh` of its own for the key itself.

```bash
ssh-keygen -t ed25519 -C "jenkins@$(hostname) fshrms-prod" \
    -f ~/.ssh/fshrms_deploy -N ""
cat ~/.ssh/fshrms_deploy.pub
```

### 3. Authorise it on GitHub

Repo → **Settings → Deploy keys → Add deploy key**. Paste the public key, title
it after the host (`jenkins@prod-vm`), and leave **"Allow write access"
unchecked**. Do this on both repos the pipelines clone:

- `fs-hrms/backend` (this repo) — every `<env>/<svc>` job
- the frontend repo — the `<env>/web` job

A deploy key is per-repository. If you'd rather use one credential for both,
use a machine-user account with read access instead — but two deploy keys is
the smaller blast radius.

### 4. Stage the private key for Jenkins

```bash
sudo install -d -m 750 -o root -g jenkins /etc/jenkins/credentials
sudo install -m 640 -o root -g jenkins \
    ~/.ssh/fshrms_deploy /etc/jenkins/credentials/github_id_ed25519
```

JCasC reads it with `${readFile:...}` at startup. Mode 640 root:jenkins — the
`jenkins` user must be able to read it, nobody else should.

### 5. Set the remotes and apply

```bash
$EDITOR .env      # GIT_REPO_URL / GIT_WEB_REPO_URL — SSH form, not HTTPS
./scripts/07-jenkins-install.sh
```

The URLs must be SSH (`git@github.com:org/repo.git`). An HTTPS URL ignores the
SSH credential and the clone fails asking for a password. The script also seeds
`/var/lib/jenkins/.ssh/known_hosts` via `ssh-keyscan`: Jenkins runs as its own
user with its own `$HOME` and does not share the login user's `known_hosts`, so
without this the first clone **hangs** at host-key verification rather than
failing.

### 6. Verify before running a job

```bash
# 1. Jenkins can authenticate to GitHub. Expect:
#    "Hi org/repo! You've successfully authenticated, but GitHub does not
#     provide shell access." — that greeting IS success for a deploy key.
sudo -u jenkins ssh -i /etc/jenkins/credentials/github_id_ed25519 \
    -o UserKnownHostsFile=/var/lib/jenkins/.ssh/known_hosts -T git@github.com

# 2. The key can actually read the repo
sudo -u jenkins env GIT_SSH_COMMAND="ssh -i /etc/jenkins/credentials/github_id_ed25519 \
    -o UserKnownHostsFile=/var/lib/jenkins/.ssh/known_hosts" \
    git ls-remote "$(grep '^GIT_REPO_URL=' .env | cut -d= -f2-)" main

# 3. JCasC picked the values up
sudo grep -E 'ENV_NAME|REGISTRY|GIT_' /etc/jenkins.env
```

Then trigger any `prod/<svc>` job with a `PROMOTE_SHA` and watch the checkout
stage. There is no webhook on prod by design — prod deploys are manual, one
promoted commit at a time.

### Troubleshooting

| Symptom | Cause |
|---------|-------|
| Build hangs at "Cloning repository", no output | `known_hosts` missing for the git host. Re-run `07-jenkins-install.sh`. |
| `Permission denied (publickey)` | Key not added as a deploy key on **that** repo, or `/etc/jenkins/credentials/github_id_ed25519` unreadable by `jenkins`. |
| `could not read Username for 'https://github.com'` | `GIT_REPO_URL` is an HTTPS URL. Switch to the `git@github.com:` form. |
| `ERROR: Could not find credentials matching github-ssh` | JCasC didn't apply — check `journalctl -u jenkins` for a casc exception, usually an unresolved `${...}` in `/etc/jenkins.env`. |
| Jobs appear under the wrong env folder | `ENV_NAME` in `.env` doesn't match the host. Fix and re-run `07`. |


## Conventions

- `set -euo pipefail` at the top of every script.
- Scripts source `.env` from the repo root if present. Required values are
  asserted with `${VAR:?...}`.
- Sudo commands run inline (`sudo …`); we don't read passwords from files.
  Run scripts as a user with sudo rights.
- No `hrms-` / `fshrms_` prefix on anything we create. The role/DB is named
  `$ENV_NAME`; containers are bare names (`redis`, `kafka`, `minio`,
  `registry`, …). Nothing is env-prefixed — there is only one environment on
  the box.
- Each cluster's sealed-secrets controller has its own keypair, so the fetched
  cert is `k8s/platform/sealed-secrets-$ENV_NAME.pub.pem`. A sealed secret from
  one VM cannot be decrypted on the other.
