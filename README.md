# Waylo

**Discover trips. Book experiences. Travel together.**

Waylo is a university travel-booking and community web application built with **Java 25** and **Spring Boot 4**. It brings together trip discovery, organizer-managed trips, secure reservations, sandbox payment testing, and private trip-group conversations.

**Live demo:** [waylo-darkraider888.onrender.com](https://waylo-darkraider888.onrender.com/feed)

> **Project notice:** Waylo is an educational/demo project. SSLCommerz runs in **Sandbox** mode for test transactions, not real-money processing.

## Features

- **Accounts and roles:** Member, Organizer, and Admin workflows using Spring Security.
- **Trip discovery:** Browse trips, destinations, and community feed content.
- **Trip management:** Organizer publishing and management workflows.
- **Reservations:** Book available trip seats and track booking status.
- **Sandbox payments:** SSLCommerz payment initiation with server-side verification, callback/IPN handling, and payment-status processing.
- **Private trip groups:** Confirmed travelers can participate in trip-specific conversations.
- **Member access controls:** Organizer-managed chat access removal and restoration, separate from payment/refund status.
- **Moderation:** Administrative review and management workflows.
- **Media uploads:** Cloudinary-backed image storage.
- **API authentication:** Optional JWT authentication for supported API endpoints, alongside session-based browser login.

## Technology Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1.1, Maven |
| Web UI | Thymeleaf, HTML, CSS, JavaScript |
| Authentication | Spring Security; sessions and JWT |
| Database | MySQL (Aiven) |
| Database migrations | Flyway |
| Image storage | Cloudinary |
| Payment testing | SSLCommerz Sandbox |
| Deployment | Render, Docker |
| Source control | GitHub |

## How It Works

1. A traveler signs in, explores available trips, and starts a reservation.
2. Waylo initiates a **test payment** through SSLCommerz Sandbox.
3. The application verifies the payment server-side before confirming the booking.
4. An eligible traveler gains access to the trip's private group.
5. The organizer can manage group access and conversations.

A gateway redirect alone must not be treated as proof of a successful payment.

## Run Locally

### Requirements

- JDK 25
- MySQL database
- Cloudinary credentials if testing image uploads
- SSLCommerz Sandbox credentials if testing online checkout

### Setup

1. Clone this repository:

   ```bash
   git clone https://github.com/Darkraider888/Waylo.git
   cd Waylo
   ```

2. Review [`.env.example`](.env.example) and configure the required environment variables in your local development environment. **Never commit real secrets.**

3. Create or configure the MySQL database and verify connectivity. Flyway manages schema migrations on application startup; back up existing data before running against an established database.

4. Run the application using the included Maven Wrapper:

   **Windows PowerShell**
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   **Linux / macOS**
   ```bash
   ./mvnw spring-boot:run
   ```

   The configured application URL and port may vary by environment.

## Deployment

Waylo uses a Dockerfile and is hosted on Render. Deployment-specific database credentials, JWT signing keys, Cloudinary credentials, and SSLCommerz Sandbox credentials are stored as private **Render environment variables** rather than committed to this repository.

- Public URL: <https://waylo-darkraider888.onrender.com/feed>
- Payment mode: **SSLCommerz Sandbox**
- Database: **Aiven MySQL**

**Security:** Never commit `.env`, access tokens, signing secrets, payment-store passwords, SMTP credentials, or database passwords. Use [`.env.example`](.env.example) only as a configuration reference.

## Current Status

- Deployed to Render with a public HTTPS address.
- SSLCommerz Sandbox checkout has been exercised.
- A confirmed traveler has accessed and posted in a private trip group.
- JWT configuration is available for API authentication; security and regression testing should continue before production use.

## Disclaimer

Built for learning, demonstration, and university project assessment. Not an audited production payment service. Only use test payment credentials with SSLCommerz Sandbox.
