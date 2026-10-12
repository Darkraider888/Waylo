# Waylo

**Discover trips. Book experiences. Travel together.**

Waylo is a university travel-booking and community platform built with **Java 25, Spring Boot 4.1.1, and Thymeleaf**. It combines trip discovery, organizer workflows, sandbox checkout, private trip conversations, account security, and administrative moderation in one web application.

**Live demo:** [waylo-darkraider888.onrender.com/feed](https://waylo-darkraider888.onrender.com/feed)  
**Source code:** [github.com/Darkraider888/Waylo](https://github.com/Darkraider888/Waylo)  
**Project contact:** [waylo.support@gmail.com](mailto:waylo.support@gmail.com)

> **Educational project:** Waylo uses **SSLCommerz Sandbox** for simulated payments. It does not process live customer payments, and its manual organizer review is not real-world identity verification.

## Contents

- [Features](#features)
- [User roles](#user-roles)
- [Technology stack](#technology-stack)
- [Architecture](#architecture)
- [Payment and group-access flow](#payment-and-group-access-flow)
- [Account recovery and JWT](#account-recovery-and-jwt)
- [Running locally](#running-locally)
- [Environment configuration](#environment-configuration)
- [Deployment](#deployment)
- [Testing](#testing)
- [Project demonstration](#project-demonstration)
- [Limitations and roadmap](#limitations-and-roadmap)

## Features

| Area | Capabilities |
| --- | --- |
| Travel discovery | Destination browsing, published trips, searching, community feed and posts |
| Accounts | Registration, sign-in, Spring Security sessions, password recovery and optional JWT API |
| Organizer workflows | Trip Manager applications, trip creation and management, and approval for an individual trip |
| Trip reservations | Seat booking and booking status management |
| Sandbox payments | SSLCommerz test checkout, server-side payment validation, callbacks/IPN and payment reviews |
| Private trip groups | Messaging for eligible confirmed travelers and organizers; access removal/restoration |
| Administration | Review applications and trip proposals, moderate trips and destinations, inspect payment exceptions and oversee private groups |
| Media | Cloudinary-backed travel images, avatars and trip covers |
| Notifications and performance | Application notifications, Flyway database migrations and caching for public destination summaries |

### Admin review dashboard

The Admin Dashboard distinguishes **Trip Manager applications** from **trip-specific organizer proposals**. Its summary displays:

- **Total pending reviews** = pending Trip Manager applications + pending trip-specific proposals.
- Pending Trip Manager applications.
- Pending trip-specific proposals.
- Manual demo verification method.

An approved single-trip applicant receives authority for the **specific approved trip**, rather than global Trip Manager permissions.

## User roles

| Role | Main responsibilities |
| --- | --- |
| **Member** | Explore trips, publish community content, book seats and participate in permitted private trip groups |
| **Trip Manager / Organizer** | Manage authorized trips and organizer workflows |
| **Single-trip organizer** | Manage one approved trip while retaining their member account role |
| **Admin** | Review proposals and applications, manage moderation, and inspect exceptional payment cases |

Access is enforced by Spring Security and application-specific permission checks, not simply by hiding links in the UI.

## Technology stack

| Layer | Technologies |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1.1, Maven Wrapper |
| Frontend | Thymeleaf, HTML, CSS, JavaScript |
| Security | Spring Security, session authentication, JWT (HS256) for REST API endpoints |
| Persistence | MySQL on Aiven, Spring Data JPA, Hibernate ORM, Flyway |
| Media | Cloudinary |
| Payments | SSLCommerz **Sandbox**, server-verified callbacks and IPN handling |
| Transactional email | Brevo HTTPS API; Resend HTTPS API available as an alternative |
| Caching | Caffeine (public destination summaries) |
| Deployment and CI | Docker, Render, GitHub Actions |

## Architecture

```text
Browser / REST client
         |
         v
Waylo - Spring Boot (Docker on Render)
  |         |          |             |
  |         |          |             +--> Brevo / Resend HTTPS email APIs
  |         |          +----------------> SSLCommerz Sandbox
  |         +---------------------------> Cloudinary media storage
  +-------------------------------------> Aiven MySQL (JPA + Flyway)
```

The browser interface uses Spring Security sessions. JWT is a separate, optional mechanism for supported `/api/v1/**` endpoints.

## Payment and group-access flow

1. A signed-in member selects an eligible published trip and starts a booking.
2. Waylo initiates a **sandbox** transaction with SSLCommerz.
3. Waylo receives gateway callback/IPN information and **verifies the transaction server-side**.
4. Only a verified, eligible booking can be confirmed and used to grant private trip-group access.
5. Authorized travelers can exchange private trip messages.
6. Authorized organizers can remove or restore a traveler's **chat access** without treating that action as a payment cancellation or refund.

A browser redirect or a success page alone is **not** payment proof. SSLCommerz Sandbox cards and transactions are for demonstration only.

**Configured IPN endpoint:**

```text
https://waylo-darkraider888.onrender.com/payments/sslcommerz/ipn
```

## Account recovery and JWT

### Forgot password

- Request a reset at [`/forgot-password`](https://waylo-darkraider888.onrender.com/forgot-password).
- Waylo generates a cryptographically random, URL-safe reset token and stores its **SHA-256 hash**, not the raw token.
- A reset link expires after **15 minutes** and is **single-use**.
- Password changes invalidate outstanding reset tokens and update the account's authentication version.
- Brevo's **HTTPS transactional-email API** is used in the configured demonstration deployment. Resend is also supported as a configurable provider.
- The response does not disclose whether a submitted email belongs to an account.

**Email sender note:** Brevo may substitute an authenticated `brevosend.com` address for an unverified custom-domain sender. Successful testing with one mailbox does not guarantee delivery to every provider or recipient. An authenticated sending domain is recommended for wider use.

### JWT API

JWT is optional and does **not** replace the browser's session-based login.

| Endpoint | Purpose |
| --- | --- |
| `POST /api/v1/auth/token` | Exchange valid account credentials for a Bearer token |
| `GET /api/v1/me` | Retrieve the authenticated account using a Bearer token |

When enabled, access tokens are signed with **HS256**, expire after **900 seconds (15 minutes)**, and are validated against the current account state and authentication version.

To enable JWT, set `WAYLO_JWT_ENABLED=true` and supply a secure, random `WAYLO_JWT_SECRET_BASE64` that decodes to **at least 32 bytes**. Never commit or publish the signing key.

## Running locally

### Requirements

- **JDK 25**
- A reachable MySQL database (MySQL 8.x or a compatible Aiven MySQL instance)
- Internet access for any enabled Cloudinary, payment, or email integrations
- Docker / a working container runtime **only if running the Testcontainers-based integration tests**

### Installation

```bash
git clone https://github.com/Darkraider888/Waylo.git
cd Waylo
```

Set the required environment variables using an IDE run configuration or your local environment. [`.env.example`](.env.example) is a reference file; Spring Boot **does not automatically load it**.

**Windows PowerShell:**

```powershell
.\mvnw.cmd spring-boot:run
```

**Linux / macOS:**

```bash
./mvnw spring-boot:run
```

When using the defaults, open `http://localhost:8080`. Flyway validates/applies the database migrations on startup, so use a dedicated development database or back up existing data before the first run.

## Environment configuration

Define secrets in IntelliJ/local environment settings or **Render > Environment**, never in committed configuration or screenshots.

| Variable | Purpose |
| --- | --- |
| `WAYLO_DB_URL` | MySQL JDBC URL, for example `jdbc:mysql://localhost:3306/waylo_dev` |
| `WAYLO_DB_USERNAME` / `WAYLO_DB_PASSWORD` | MySQL credentials |
| `SPRING_PROFILES_ACTIVE` | Set to `deployment` on Render |
| `WAYLO_PUBLIC_URL` | Public base URL, e.g. `https://waylo-darkraider888.onrender.com` (no `/feed`) |
| `CLOUDINARY_URL` | Cloudinary credentials/connection URL for persistent media |
| `WAYLO_SSLCOMMERZ_ENABLED` | Enables sandbox integration when set to `true` |
| `WAYLO_SSLCOMMERZ_STORE_ID` / `WAYLO_SSLCOMMERZ_STORE_PASSWORD` | **Sandbox** store credentials |
| `WAYLO_JWT_ENABLED` | Enables supported JWT API endpoints when set to `true` |
| `WAYLO_JWT_SECRET_BASE64` | Secure Base64 signing key; at least 32 decoded bytes |
| `WAYLO_PASSWORD_RESET_PROVIDER` | `brevo` or `resend` (code default: `resend`) |
| `WAYLO_BREVO_API_KEY` | Brevo API v3 key for HTTPS email delivery |
| `WAYLO_BREVO_FROM_EMAIL` / `WAYLO_BREVO_FROM_NAME` | Verified/accepted Brevo sender identity |
| `WAYLO_RESEND_API_KEY` / `WAYLO_RESEND_FROM` | Resend configuration when using `resend` |
| `WAYLO_GOOGLE_MAPS_EMBED_KEY` | Optional Google Maps Embed API key |
| `WAYLO_DB_POOL_SIZE` | Optional connection pool-size override for the deployment profile |

**Example of nonsensitive flags:**

```properties
SPRING_PROFILES_ACTIVE=deployment
WAYLO_PUBLIC_URL=https://waylo-darkraider888.onrender.com
WAYLO_PASSWORD_RESET_PROVIDER=brevo
WAYLO_JWT_ENABLED=true
WAYLO_SSLCOMMERZ_ENABLED=true
```

These flags alone are not enough to enable the integrations: the respective **private keys and credentials** must also be configured. Do not copy real secrets into this README, GitHub issues, CI logs or pull requests.

## Deployment

Waylo is deployed as a **Docker web service on Render** and connects to **Aiven MySQL**, **Cloudinary**, **SSLCommerz Sandbox** and its configured email API.

- **Website:** <https://waylo-darkraider888.onrender.com/feed>
- **Health endpoint:** <https://waylo-darkraider888.onrender.com/healthz> (returns `OK` when the database health query succeeds)
- **Git branch:** `master`
- **Application profile:** `deployment`
- **Environment secrets:** Managed in Render, not GitHub

> **Important:** The repository's current [`render.yaml`](render.yaml) is a **prepared paid-resource Blueprint** (2 GB service and a persistent disk). It is **not** the Free-service configuration. Do not apply that Blueprint unintentionally. For Free staging, create/configure a regular Render web service using `Dockerfile`, selecting the Free plan explicitly.

**Free-plan limitation:** An idle Render Free instance can spin down; its next request may be slow while Spring Boot starts and reconnects to MySQL. Intermittent errors should be diagnosed from Render runtime logs rather than assumed to be normal cold starts.

For persistent production media, use Cloudinary. A Free service's local filesystem should not be relied on for durable uploads.

## Testing

GitHub Actions defines a Java 25 build that runs Maven verification (including isolated MySQL tests through Testcontainers) and builds a Docker image without publishing it.

**Windows:**

```powershell
.\mvnw.cmd clean verify
```

**Linux / macOS:**

```bash
./mvnw clean verify
```

The full integration suite requires a functioning Docker/container runtime. If Docker is unavailable locally, Testcontainers-based tests may error even while the Java source compiles. The Docker packaging process can be checked separately with `-DskipTests package`, but that is **not** a substitute for running tests.

Suggested end-to-end checks:

1. Member registration and session login.
2. Admin review of a pending Trip Manager application and a single-trip proposal, including separate dashboard counters.
3. Trip publication, reservation and SSLCommerz **Sandbox** callback verification.
4. Confirmed booking to private-group access; sending messages; removal and restoration of chat privileges.
5. Password-reset email delivery, expiration, one-time use and new-password login.
6. JWT token creation, valid/expired/invalid tokens and role restrictions.
7. Render health checks and logs following every deploy.

## Project demonstration

A typical university presentation can follow this sequence:

1. Show the live Feed, Explore and trip-detail pages.
2. Demonstrate the different roles and the organizer approval workflow.
3. Use an **SSLCommerz Sandbox** test transaction to complete a trip booking.
4. Show the confirmed traveler's private group and organizer chat moderation.
5. Show the Admin Dashboard's **Total pending reviews**, **Trip Manager applications** and **Trip-specific proposals** counters.
6. Demonstrate a password-reset email, then explain JWT API authentication.

If using Render Free, visit the website and verify the database health check **before** the presentation to allow the application time to wake up. A recorded backup demonstration is useful if an external service is temporarily unavailable.

## Limitations and roadmap

- **Sandbox only:** SSLCommerz payments are simulations, not real-money transactions.
- **Demo identity review:** Organizer applications do not perform real NID/passport validation.
- **External services:** Email delivery and gateway callbacks depend on third-party availability; a free email sender may be limited or rewritten by the provider.
- **Hosting:** Render Free can incur cold starts, limited memory and variable response times.
- **Planned UI additions:** A reusable footer, Help Center, support and policy pages, and a developer/portfolio page. These are **planned**, not claimed as completed.
- **Before production:** Additional load testing, security review, monitoring, email-domain authentication and a live-payment readiness assessment would be required.

## License and use

This repository documents an **educational/university project**. No open-source license is declared here. Do not assume permission to redistribute or commercially reuse the code without the owner's approval.

---

**Waylo - Explore. Connect. Go.**
