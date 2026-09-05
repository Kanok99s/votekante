# VoteKante

An anonymous, one-person-one-vote polling platform built with **Spring Boot 3**,
**Spring Security**, **Spring Data JPA**, **Thymeleaf** and **MySQL** — including
Kahoot-style **community polls** that anyone can join by code, plus a fully
**public dashboard** you can show off to anyone.

> ### 🚀 Try it live — no account needed to look around
>
> **<https://votekante.onrender.com>**
>
> Open the link and you land straight on the live polls dashboard. Browse open
> polls, watch results update in real time, or type a shared poll code into the
> "Have a poll code?" box. Accounts are only required to *vote*, *create* or
> *manage* polls.

---

## What's in this version

| Capability | Who can do it |
|------------|----------------|
| Open the site and see the **public dashboard** of open polls | Anyone (no login) |
| View a shared poll by **join code** (e.g. `K7X2Q4`) | Anyone (no login) |
| Watch **live results** with charts (`/results`) | Anyone (no login) |
| **Vote** once per poll | Signed-in **voter** account |
| **Create** a community poll (question + 2–12 options, instantly open) | Any signed-in user |
| **Manage** polls — open/close/delete from "My polls" | The poll's creator |
| **Official elections** — parties, open/close voting | **Admin** |

### How community polls work

- Any signed-in user can create a poll: a question, an optional description and
  2–12 answer options. It opens immediately and appears on the public dashboard.
- The poll gets a unique 6-character **join code** (e.g. `K7X2Q4`) plus a
  shareable link `/join/K7X2Q4`.
- Share the code or link with anyone — they can jump straight to the poll, see
  the question and the live tally, and sign in to cast their anonymous vote.
- Creators see their polls in **My polls** with live vote counts, a copy/share
  button, and open/close/delete controls.
- The code is dead-simple: open the site, enter the code, done. No poll search,
  no admin approval.

### What stays classic

- **Anonymous ballots.** Votes live in a table with **no user reference** — the
  schema makes it impossible to link a ballot to the voter, not merely "policy".
- **One person, one vote.** A database-level unique constraint enforces one
  ballot per account per poll/election.
- **Live results.** Counts update instantly with every vote (Chart.js bar chart
  on the results page).

---

## Quick start

### 🌍 Just use the live site

**<https://votekante.onrender.com>**

- Try the **public dashboard** — you'll see it without signing in.
- **Create an account**, click **+ Create a poll**, and share its code with a
  friend (or a second browser) to see voting and live results in action.
- Render's free tier sleeps after ~15 idle minutes, so the very first request
  after a nap takes ~30 s to wake the app.

### 🐳 Run it yourself with Docker (no Java/Maven/MySQL needed)

Anyone with [Docker](https://docs.docker.com/get-docker/) can run the whole app
(web server **and** its MySQL database):

```bash
git clone <your-repo-url>
cd votekante
docker compose up --build
```

Then open **http://localhost:8080**.

| Role  | Username | Password   | Notes                                   |
|-------|----------|------------|-----------------------------------------|
| Admin | `admin`  | `admin123` | Open the demo election, manage parties  |
| Voter | —        | —          | Register your own account from the site |

First boot seeds a closed demo election; sign in as admin and press
**Open voting** to try the flow.

```bash
docker compose down          # stop
docker compose down -v       # stop and DELETE all data (fresh start)
docker compose up --build    # rebuild after code changes
```

Set `ADMIN_PASSWORD` and MySQL credentials via a `.env` file next to
`docker-compose.yml`:

```dotenv
ADMIN_USERNAME=admin
ADMIN_PASSWORD=change-me
DB_PASSWORD=votekante
```

### ☕ Run locally with Maven + MySQL

Prerequisites: JDK 17, Maven 3.8+, MySQL 8 running locally.

```sql
CREATE DATABASE IF NOT EXISTS votekante CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Set your MySQL credentials in `src/main/resources/application.properties`
(defaults: user `root`, password `root`), then:

```bash
mvn spring-boot:run
```

Confirm it booted: **GET http://localhost:8080/hello** → "Hello from VoteKante!
The server is running."

---

## Deploying to Render

Render does **not** offer managed MySQL, so first create a MySQL database on an
external provider (free tiers: Aiven, Railway, Clever Cloud, TiDB Cloud; paid:
DigitalOcean, AWS RDS…). Copy its host, database name, user and password.

1. **Push to GitHub.** The repository root must be the project folder (the
   `Dockerfile`, `render.yaml` and `docker-compose.yml` at the top level).
2. On [render.com](https://render.com): **New + → Blueprint**, then pick the
   GitHub repository. Render reads `render.yaml`.
3. In the new service → **Environment**, set the "sync: false" values:

   | Variable        | Example                                                    |
   |-----------------|-------------------------------------------------------------|
   | `DB_URL`        | `jdbc:mysql://host:3306/votekante?useSSL=true&serverTimezone=UTC` |
   | `DB_USERNAME`   | your MySQL user                                            |
   | `DB_PASSWORD`   | your MySQL password                                        |
   | `ADMIN_USERNAME`| an admin login for the live site                           |
   | `ADMIN_PASSWORD`| a **strong** password for it                               |

   `PORT` is injected by Render automatically; `SEED_DEMO=false` keeps demo data
   off the live site.
4. Render redeploys on every push. Your site is at
   **https://votekante.onrender.com**; health check is **GET /hello**.

---

## 1. Architecture

```
Browser (Thymeleaf pages)
        │  GET/POST (form login + CSRF)
        ▼
Spring MVC controllers            → templates/  (Thymeleaf + plain CSS)
        │
Spring Security (BCrypt, roles VOTER/ADMIN, form login, CSRF)
        │
Service layer (@Transactional)
        │
Spring Data JPA repositories
        ▼
MySQL database
```

### What's public vs. behind an account

| Route group           | Access                                              |
|-----------------------|-----------------------------------------------------|
| `/` , `/polls/browse` | **Public** — dashboard of open polls (read-only)    |
| `/poll/{code}`, `/join/{code}`, `/polls/join` | **Public** — reach a shared poll by code |
| `/results`, `/results/**` | **Public** — live results for everyone          |
| `/voter/**` (voting)  | `ROLE_VOTER` only                                   |
| `/polls/new`, `/polls`, `/polls/mine`, `/polls/{id}/toggle|delete` | Any signed-in user |
| `/admin/**`           | `ROLE_ADMIN` only                                   |
| `/login`, `/register` | Public auth pages                                   |

### Package layout (com.votekante)

| Package       | Contents                                                            |
|---------------|---------------------------------------------------------------------|
| `config`      | `SecurityConfig`, `DataSeeder` (first-boot admin + demo data)       |
| `entities`    | `User`, `Role`, `Party`, `Election` (+`creator`, `joinCode`), `HasVoted`, `Vote` |
| `repositories`| Spring Data JPA interfaces (elections fetch-joined with parties/creator) |
| `services`    | `VoteService`, `PollService` (community polls), `ElectionService`, `ResultService` |
| `controllers` | `HomeController`, `AuthController`, `VoterController`, `AdminController`, `ResultsController`, `PollController` (create/manage/join), `PublicController` (guest browse + poll view) |
| `resources`   | `application.properties`, `templates/` (Thymeleaf), `static/css/`   |

### Data model

| Table         | Purpose                                                                 |
|---------------|-------------------------------------------------------------------------|
| `app_user`    | Accounts: username, BCrypt password hash, role (VOTER/ADMIN).           |
| `election`    | A round of voting; `open` flag, name/description, and — for community polls — the `creator` user and unique `joinCode`. |
| `party`       | Options/candidates, each belonging to one election or poll.            |
| `has_voted`   | Receipts: (user_id, election_id, voted=true). Unique on (user, election).|
| `vote`        | **Anonymous ballots**: random-UUID id, party_id, election_id.           |

---

## 2. Why anonymity is structural, not just "policy"

A ballot must prove *"this voter voted in this poll"* (for one-vote enforcement)
while never revealing *what* they chose.

- `has_voted` stores only the **fact**: `user 42 voted in election 3`. Its unique
  `(user_id, election_id)` constraint enforces one person, one vote at the
  database level — safe against double-submits and races.
- `vote` stores only the **content**: `election 3 → party 7`. It has **no
  `user_id` column and no relationship to `User`**.

Because the tables are separate and `vote` never references a user, **no SQL
join can connect a ballot to the person who cast it.** Two more choices reinforce
this:

1. **Random UUID primary keys on `vote`.** An auto-increment id would let an
   observer watching inserts correlate ballots with voters. Random UUIDs remove
   that correlation.
2. **Results are pure `COUNT(*) ... GROUP BY party_id`** over `vote` — the query
   never touches identity data.

> After voting, the site confirms *"your vote was recorded"* without ever showing
> which option was chosen.

`VoteService.castVote(userId, electionId, partyId)` runs in a single
`@Transactional` method: election open → party belongs to that election → no
existing `has_voted` receipt → insert anonymous `vote` **and** the receipt
together, so a failure mid-way rolls everything back.

---

## 3. Configuration reference

| Variable        | Local default (application.properties)  | Meaning              |
|-----------------|-----------------------------------------|----------------------|
| `DB_URL`        | `jdbc:mysql://localhost:3306/votekante?...` | JDBC URL      |
| `DB_USERNAME`   | `root`                                  | MySQL user           |
| `DB_PASSWORD`   | `root`                                  | MySQL password       |
| `PORT`          | `8080`                                  | HTTP port            |
| `SEED_DEMO`     | `true`                                  | Seed demo election   |
| `ADMIN_USERNAME`| `admin`                                 | First admin login    |
| `ADMIN_PASSWORD`| `admin123`                              | First admin password |
| `JPA_DDL_AUTO`  | `update`                                | Hibernate schema mode|

---

## 4. Security notes

- Passwords are BCrypt-hashed (`BCryptPasswordEncoder`, strength 10).
- **Public by design:** the open-poll dashboard, joining a poll by code, and the
  results pages need no account. **Voting and creating/managing polls do.** The
  public pages render read-only — no ballot forms reach guests.
- Role-based access: `ROLE_ADMIN` → `/admin/**`; `ROLE_VOTER` → `/voter/**`
  (casting). Registration only ever creates `VOTER` accounts.
- CSRF protection is enabled; every POST form carries the token.
- An election's candidate list is **locked while voting is open**, and a party
  that has received ballots can no longer be edited or deleted — preventing
  falsification after the fact.
- Poll codes only open the poll for viewing/joining; poll management (open,
  close, delete) is restricted to the creator's own polls.
- Results queries aggregate the anonymous `vote` table only.

> Change the default `admin` password and keep `SEED_DEMO=false` before any
> real-world use.
