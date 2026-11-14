# Deploying the API to the VPS

Server: Contabo VPS, Ubuntu 24.04, IP `169.58.82.124`.
Do the steps in order. Each one ends with a **Check**: don't move on until it passes.

```

- **Nginx** is the front door. It's the only program the internet can talk to.
- **Docker** runs the API and the database in containers, so the server needs no Java or Postgres installed.
- **The database has no open port at all.** Only the API container can reach it.

**Safety net:** if you ever lock yourself out of SSH, Contabo's control panel has a **VNC console** that logs in directly, like a monitor plugged into the server. Find it before you start.

---

## Step 1: Secure the server

You're logging in as `root` with a password. Bots try passwords against every public server all day (`sudo grep "Failed password" /var/log/auth.log | wc -l` after a day will show you). We'll switch to: your own user + SSH keys, no root login, no passwords.

### 1a. Update everything (on the server, as root)

```bash
apt update && apt upgrade -y
```

### 1b. Create your own user with sudo rights

```bash
adduser alex
usermod -aG sudo alex
```

`adduser` asks for a password. You'll use it for `sudo`, not for logging in.

### 1c. Create an SSH key (on your Windows machine, in PowerShell)

An SSH key is a pair of files: a **private key** that never leaves your laptop, and a **public key** you give to the server. It's far stronger than any password.

```powershell
ssh-keygen -t ed25519 -C "alex-laptop"
```

Press Enter to accept the location. Set a passphrase when asked (it protects the key if your laptop is stolen).

Copy the **public** key to the new user on the server:

```powershell
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh root@169.58.82.124 "mkdir -p /home/alex/.ssh && cat >> /home/alex/.ssh/authorized_keys && chown -R alex:alex /home/alex/.ssh && chmod 700 /home/alex/.ssh && chmod 600 /home/alex/.ssh/authorized_keys"
```

Add a shortcut so you can type `ssh vps` instead of the full address. Open (or create) `C:\Users\<you>\.ssh\config` and add:

```
Host vps
    HostName 169.58.82.124
    User alex
    IdentityFile ~/.ssh/id_ed25519
```

**Check:** in a **new** PowerShell window, `ssh vps` logs you in as `alex` without asking for the server password (it may ask for your key's passphrase). Then run `sudo whoami`: it should print `root`.

> Keep your root session open until this check passes. If something is wrong, you can still fix it from there.

### 1d. Turn off root login and password login

On the server:

```bash
sudo nano /etc/ssh/sshd_config.d/01-hardening.conf
```

Paste:

```
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
```

Why the `01-` prefix: sshd uses the **first** value it reads for each setting, and reads these files alphabetically. Ubuntu cloud images ship a `50-cloud-init.conf` that turns passwords **on**, so a file named `99-...` would lose.

Test the config, then restart SSH:

```bash
sudo sshd -t && sudo systemctl restart ssh
sudo sshd -T | grep -E 'permitrootlogin|passwordauthentication'
```

**Check:** the last command prints `permitrootlogin no` and `passwordauthentication no`. In a new window, `ssh vps` still works, and `ssh root@169.58.82.124` is refused.

### 1e. Firewall

`ufw` blocks every port you don't explicitly allow.

```bash
sudo ufw allow OpenSSH      # 22: so you don't lock yourself out
sudo ufw allow 80/tcp       # HTTP
sudo ufw allow 443/tcp      # HTTPS (for later)
sudo ufw enable
sudo ufw status verbose
```

**Check:** status shows `active` with only those three allowed.

### 1f. Ban repeated failed logins, and auto-install security updates

```bash
sudo apt install -y fail2ban
sudo systemctl enable --now fail2ban
sudo dpkg-reconfigure -plow unattended-upgrades   # answer Yes
```

`fail2ban` watches the SSH log and blocks IPs that keep failing. `unattended-upgrades` installs security patches every day.

**Check:** `sudo fail2ban-client status sshd` shows the jail is running.

---

## Step 2: Install Docker

From Docker's official repository (Ubuntu's own `docker.io` package is older):

```bash
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

Let `alex` run Docker without `sudo`, then log out and back in so it takes effect:

```bash
sudo usermod -aG docker alex
exit
```

> Being in the `docker` group is effectively root access (Docker can mount any file). That's fine for your own account; never add other people to it.

**Check:** after `ssh vps` again, `docker run --rm hello-world` prints "Hello from Docker!".

### Docker and the firewall: an important gotcha

Ports that Docker publishes **skip ufw entirely**. If `compose.yaml` said `"8081:8081"`, the API would be open to the internet even though ufw blocks 8081. That's why it says `"127.0.0.1:8081:8081"`: bound to the server's own loopback address, reachable only by Nginx and SSH tunnels.

---

## Step 3: Run the API and database

### 3a. Get the code

If the GitHub repo is **public**:

```bash
cd ~
git clone https://github.com/Guandaru-Kabugi/personalwebsitebackend.git
cd personalwebsitebackend
```

If it's **private**, give the server its own read-only key (a "deploy key"):

```bash
ssh-keygen -t ed25519 -C "vps-deploy" -f ~/.ssh/github_deploy -N ""
cat ~/.ssh/github_deploy.pub
```

On GitHub: repo → Settings → Deploy keys → Add deploy key → paste it, leave "Allow write access" **off**. Then:

```bash
printf 'Host github.com\n  IdentityFile ~/.ssh/github_deploy\n' >> ~/.ssh/config
git clone git@github.com:Guandaru-Kabugi/personalwebsitebackend.git
cd personalwebsitebackend
```

### 3b. Create the secrets file

```bash
cp .env.example .env
openssl rand -hex 32        # copy the output
nano .env                   # paste it as POSTGRES_PASSWORD
chmod 600 .env              # only you can read it
```

**Write this password down somewhere safe** (a password manager). You'll need it to restore backups.

### 3c. Build and start

```bash
docker compose up -d --build
```

The first build takes a few minutes (it downloads Maven dependencies). `-d` runs it in the background.

```bash
docker compose ps                 # both services should be "running" / db "healthy"
docker compose logs -f api        # watch Spring start; Ctrl+C to stop watching
```

**Check:** the log ends with `Started PersonalwebsitebackendApplication`, and:

```bash
curl http://127.0.0.1:8081/api/public/skills
```

prints `[]` (an empty list: the database is new).

---

## Step 4: Put Nginx in front

```bash
sudo apt install -y nginx
sudo cp deploy/nginx/proxy-to-api.conf /etc/nginx/snippets/
sudo cp deploy/nginx/personal-website.conf /etc/nginx/sites-available/
sudo ln -s /etc/nginx/sites-available/personal-website.conf /etc/nginx/sites-enabled/
sudo rm /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

`nginx -t` checks the config for mistakes before reloading. Always run it.

**Check from your Windows machine** (not the server):

```powershell
curl.exe http://169.58.82.124/api/public/skills     # []
curl.exe -i http://169.58.82.124/api/admin/tags     # 404 Not Found: admin is hidden
curl.exe http://169.58.82.124:8081/api/public/skills  # fails (refused or timeout): 8081 is not public
```

Your API is live. 🎉

---

## Step 5: Add content through an SSH tunnel

The admin API is blocked at Nginx, but you can still reach it safely. An SSH tunnel makes `localhost:8081` on your laptop forward, encrypted, to `127.0.0.1:8081` on the server.

1. Stop Spring Boot on your laptop if it's running (both would want port 8081).
2. In PowerShell, open the tunnel and leave the window open:

   ```powershell
   ssh -N -L 8081:127.0.0.1:8081 vps
   ```

3. Now anything on your laptop that talks to `localhost:8081` is talking to **production**:
   - Run the Postman collection (`postman/personal-website.postman_collection.json`) to load the sample data.
   - Or run the CMS dashboard (`npm run dev` in `cms-dashboard`) and edit content in the browser.

4. Close the tunnel with Ctrl+C when you're done.

> While the tunnel is open, deletes in the dashboard or Postman are real. The dashboard doesn't know it's talking to production.

**Check:** `curl.exe http://169.58.82.124/api/public/profile` (no tunnel needed) returns your profile.

---

## Step 6: Deploy an update

After you push new code to GitHub:

```bash
ssh vps
cd ~/personalwebsitebackend
git pull
docker compose up -d --build     # rebuilds and restarts only what changed
docker image prune -f            # delete old image layers to save disk
```

The database volume isn't touched, so your data stays.

If you changed anything in `deploy/nginx/`, copy it again and reload (the commands from step 4).

---

## Step 7: Backups

Your data lives in one Docker volume on one server. Back it up.

Try a backup by hand:

```bash
bash deploy/backup.sh
ls -lh ~/backups
```

Run it every night at 02:30:

```bash
crontab -e
```

Add this line:

```
30 2 * * * /bin/bash /home/alex/personalwebsitebackend/deploy/backup.sh >> /home/alex/backups/backup.log 2>&1
```

A backup on the same server doesn't help if the server dies. Copy them to your laptop now and then (from PowerShell):

```powershell
scp "vps:~/backups/db-*.sql.gz" .
```

**Restoring** (practice this once, so you know it works before you need it). A dump recreates the tables, so it must go into an empty database:

```bash
set -a; source .env; set +a
docker compose stop api       # nothing should write while you restore
docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'
gunzip -c ~/backups/db-2026-11-01_0230.sql.gz | docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
docker compose start api
```

The `DROP SCHEMA` line deletes everything currently in the database, so double-check you picked the right backup file.

Contabo also offers server snapshots in the control panel. Take one before big changes.

---

## Step 8 (later): Domain and HTTPS

Once you have a domain:

1. At your domain registrar, add an **A record**: `api.yourdomain.com` (or `yourdomain.com`) → `169.58.82.124`.
2. In `/etc/nginx/sites-available/personal-website.conf`, change `server_name _;` to `server_name api.yourdomain.com;`, then `sudo nginx -t && sudo systemctl reload nginx`.
3. Get a free certificate from Let's Encrypt; certbot edits the Nginx config and renews automatically:

   ```bash
   sudo apt install -y certbot python3-certbot-nginx
   sudo certbot --nginx -d api.yourdomain.com
   ```

---

## Step 9: Turn on login (Spring Security)

Once the auth code is deployed, `/api/admin/**` is protected by a login, so Nginx no longer needs to hide it, and you no longer need the SSH tunnel. Details of the auth design are in [AUTH.md](AUTH.md).

### 9a. Add the new settings to `.env` (on the server)

```bash
cd ~/personalwebsitebackend
git pull
openssl rand -base64 48     # copy the output: this is JWT_SECRET
nano .env
```

Use a **new** JWT secret here, not the one from your laptop. Never reuse a secret that has been in a file in git.

### 9b. Rebuild and watch it create your admin account

```bash
docker compose up -d --build
docker compose logs -f api
```

**Check:** the log shows `Created the first admin account 'alex'`. Then delete the three `BOOTSTRAP_ADMIN_*` lines from `.env`, so the password isn't left lying around. They're only read when no admin exists.

### 9c. Let Nginx forward the auth and admin endpoints

The server's Nginx file now has HTTPS lines that certbot added, so **edit** it rather than copying the repo version over it:

```bash
sudo nano /etc/nginx/sites-available/personal-website.conf
```

1. At the top, next to the existing `limit_req_zone` line, add:
   ```
   limit_req_zone $binary_remote_addr zone=login:10m rate=10r/m;
   ```
```bash
sudo nginx -t && sudo systemctl reload nginx
```
## Everyday commands

| Task | Command (in `~/personalwebsitebackend`) |
|---|---|
| Status of containers | `docker compose ps` |
| API logs (live) | `docker compose logs -f api` |
| Last 100 log lines | `docker compose logs --tail 100 api` |
| Restart the API | `docker compose restart api` |
| Stop everything | `docker compose down` (data is kept) |
| Open a SQL shell | `docker compose exec db psql -U personalwebsite -d personalwebsite` |
| Disk usage | `df -h` and `docker system df` |
| Nginx errors | `sudo tail -f /var/log/nginx/error.log` |
| Who's been blocked | `sudo fail2ban-client status sshd` |

> **Never run `docker compose down -v`.** The `-v` deletes volumes, which means your database.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `502 Bad Gateway` from Nginx | The API isn't running or is still starting. `docker compose ps` and `docker compose logs api`. |
| API restarts in a loop | Usually a config error. The first `ERROR` in `docker compose logs api` says what. |
| `password authentication failed` in API logs | `.env` was changed after the database was first created. Postgres only reads `POSTGRES_PASSWORD` the first time the volume is created. |
| `Permission denied (publickey)` | Wrong key or user. `ssh -v vps` shows which key it tries. If locked out, use Contabo's VNC console. |
| `bind: address already in use` on `ssh -L` | Something on your laptop already uses 8081 (probably your local Spring Boot). |
