# VoteKante

An anonymous, one-person-one-vote election platform built with **Spring Boot 3**,
**Spring Security**, **Spring Data JPA**, **Thymeleaf** and **MySQL**.

- Voters register, sign in, and cast exactly **one ballot per election**.
- Ballots are stored **anonymously** — the schema makes it impossible to link a
  vote to a voter, not merely "policy".
- Admins create elections, manage the candidate parties, open/close voting, and
  watch **live results** (Chart.js bar chart).
- Runs as a single jar or a Docker image — deployable to **Render** or any
  Docker host.

---

## Quick start

### 🐳 Easiest — run it with Docker (no Java/Maven/MySQL needed)

Anyone who has [Docker](https://docs.docker.com/get-docker/) can run the whole
app (web server **and** its MySQL database) with two commands:

```bash
git clone <your-repo-url>
cd votekante
docker compose up --build
```

Then open **http://localhost:8080**.

| Role  | Username | Password   | Notes                                     |
|-------|----------|------------|-------------------------------------------|
| Admin | `admin`  | `admin123` | Open the demo election, manage parties    |
| Voter | —        | —          | Register your own account from the site   |

First boot seeds one closed demo election; sign in as admin and press
**Open voting** to try the whole flow.

Other useful commands:

```bash
docker compose down          # stop
docker compose down -v       # stop and DELETE all data (fresh start)
docker compose up --build    # rebuild after code changes
```

You can change the admin password and MySQL credentials by creating a `.env`
file next to `docker-compose.yml`:

```dotenv
ADMIN_USERNAME=admin
ADMIN_PASSWORD=change-me
DB_PASSWORD=votekante
```

### ☕ Classic — run locally with Maven + MySQL

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

### 🌍 Web version — deploy the live site to Render (free)

Anyone can then use it at a public URL — no download required.

> Render does **not** offer managed MySQL, so first create a MySQL database on
> an external provider (free tiers: Aiven, Railway, Clever Cloud, TiDB Cloud;
> paid: DigitalOcean, AWS RDS…). Copy its host, database name, user, password.

1. **Push to GitHub.** Make the repository root the project folder (the
   `Dockerfile`, `render.yaml` and `docker-compose.yml` must sit at the root).
   Don't commit real passwords — a `.gitignore` already excludes `.env`.
2. On [render.com](https://render.com): **New + → Blueprint**, then pick the
   GitHub repository. Render reads `render.yaml` and creates the service.
3. Open the new service → **Environment** and add the values marked "sync:
   false" in `render.yaml`:

   | Variable        | Example                                                    |
   |-----------------|-------------------------------------------------------------|
   | `DB_URL`        | `jdbc:mysql://host:3306/votekante?useSSL=true&serverTimezone=UTC` |
   | `DB_USERNAME`   | your MySQL user                                            |
   | `DB_PASSWORD`   | your MySQL password                                        |
   | `ADMIN_USERNAME`| an admin login for the live site                           |
   | `ADMIN_PASSWORD`| a **strong** password for it                               |

   `PORT` is injected by Render automatically; `SEED_DEMO=false` is set by the
   blueprint so no demo data appears on your live site.
4. Render redeploys. Health check is **GET /hello**; your site is at
   `https://votekante.onrender.com`.

> Free-tier note: Render free web services sleep after ~15 idle minutes, so the
> first request after a nap takes ~30 s to wake the JVM.

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

### Package layout (com.votekante)

| Package       | Contents                                                            |
|---------------|---------------------------------------------------------------------|
| `config`      | `SecurityConfig`, `DataSeeder` (first-boot admin + demo data)       |
| `entities`    | `User`, `Role`, `Party`, `Election`, `HasVoted`, `Vote`             |
| `repositories`| Spring Data JPA interfaces                                          |
| `services`    | Business logic incl. the transactional `VoteService.castVote`       |
| `controllers` | Auth, Voter, Admin, Results, Home + global error handling           |
| `resources`   | `application.properties`, `templates/` (Thymeleaf), `static/css/`   |

### Data model

| Table         | Purpose                                                                 |
|---------------|-------------------------------------------------------------------------|
| `app_user`    | Accounts: username, BCrypt password hash, role (VOTER/ADMIN).           |
| `election`    | A round of voting; `open` flag + name/description.                      |
| `party`       | Candidate options, each belonging to one election.                      |
| `has_voted`   | Receipts: (user_id, election_id, voted=true). Unique on (user, election).|
| `vote`        | **Anonymous ballots**: random-UUID id, party_id, election_id.           |

---

## 2. The anonymity design decision — why `vote` and `has_voted` are separate tables

This is the heart of the system, so it deserves a careful explanation.

**A ballot must prove "this voter voted in this election" (for one-vote
enforcement) while never revealing *what* they chose.**

- `has_voted` stores the *fact*: `user 42 voted in election 3`. Its unique
  `(user_id, election_id)` constraint is what enforces *one person, one vote* —
  at the database level, robust against double-submits and races.
- `vote` stores the *content*: `election 3 → party 7`. It deliberately has
  **no `user_id` column and no relationship to `User`**.

Because the two tables are separate and `vote` never references a user, **no SQL
join can ever connect a ballot to the person who cast it.** The only shared
thing between the tables is the election id, which correlates *"who voted"* with
*"how many ballots exist"* — never *"who chose what"*.

Two more structural choices reinforce the property:

1. **Random UUID primary key on `vote`.** An auto-increment id would leak
   ordering: the Nth inserted ballot would line up with the Nth voter who
   pressed "submit", letting an observer correlate ballots with voters by
   watching the database. Random UUIDs remove that correlation.
2. **Results are pure `COUNT(*) ... GROUP BY party_id`** over `vote`. The
   query in `VoteRepository` never touches `User`, `HasVoted` or any identity
   data, so the results page is anonymous by construction.

> The confirmation page drives the point home to voters: after casting, they
> see "thank you, your vote was recorded" **without any indication of what they
> selected**.

### The voting transaction

`VoteService.castVote(userId, electionId, partyId)` runs in **one `@Transactional`**
method:

1. Election must exist and be **open**.
2. Party must exist **and belong to that election**.
3. `has_voted` must not already contain (user, election).
4. Insert the anonymous `vote` **and** the `has_voted` receipt.
5. The `(user_id, election_id)` unique constraint is the backstop that defeats
   concurrent double-submits.

Because steps 4 happen inside a single transaction, a failure half-way can never
leave the system in the state "ballot recorded but voter not marked" or the
reverse — it all rolls back together.

---

## 3. Configuration reference

| Variable       | Local default (application.properties)  | Meaning              |
|----------------|-----------------------------------------|----------------------|
| `DB_URL`       | `jdbc:mysql://localhost:3306/votekante?...` | JDBC URL      |
| `DB_USERNAME`  | `root`                                  | MySQL user           |
| `DB_PASSWORD`  | `root`                                  | MySQL password       |
| `PORT`         | `8080`                                  | HTTP port            |
| `SEED_DEMO`    | `true`                                  | Seed demo election   |
| `ADMIN_USERNAME`| `admin`                                | First admin login    |
| `ADMIN_PASSWORD`| `admin123`                             | First admin password |
| `JPA_DDL_AUTO` | `update`                                | Hibernate schema mode|

Under Docker Compose these same variables are overridden from the
`environment:` block (or your `.env` file); on Render from the dashboard.

---

## 4. Security notes

- Passwords are BCrypt-hashed (`BCryptPasswordEncoder`, strength 10).
- Role-based access: `ROLE_ADMIN` → `/admin/**`, `ROLE_VOTER` → `/voter/**`;
  `/results/**` is visible to every signed-in user. Registration only ever
  creates `VOTER` accounts — admins are provisioned via config.
- CSRF protection is enabled; every POST form carries the token.
- The candidate list of an election is **locked while voting is open**, and a
  party that has received ballots can no longer be edited or deleted — both
  prevent falsifying a result after the fact.
- Results queries aggregate the anonymous `vote` table only.

> Change the default `admin` password and set `SEED_DEMO=false` before any
> real-world use.
