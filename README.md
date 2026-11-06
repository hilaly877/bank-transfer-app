# Bank Transfer App

A server-rendered bank account and transfer application built with Spring Boot,
Thymeleaf, Spring Data JPA, and MySQL. The application demonstrates
transaction-safe money movement, repository queries, validation, and separate
auditing of failed transfers.

## Features

- Dashboard with customer balances, total bank balance, and transaction counts
- Search customers by name
- Create customers with account-number and email uniqueness checks
- Transfer money between two different accounts
- Deposit and withdraw money
- View an account's transaction history and total successful amount sent
- Filter successful transfers by a minimum amount
- Simulate a transfer failure to demonstrate rollback behavior
- Record failed-transfer audit entries independently after rollback
- Seed three sample customers on the first startup

## Technology Stack

- Java 21
- Spring Boot 4.0.9
- Spring MVC
- Thymeleaf
- Spring Data JPA / Hibernate
- MySQL
- Lombok
- Maven

## Prerequisites

- JDK 21 or later
- Maven 3.9 or later
- MySQL Server 8 or later

The application expects MySQL to be available on `localhost:3306`. The
configured JDBC URL creates the `bank_db` database automatically if it does not
already exist.

## Configuration

The default settings are in
[`application.properties`](src/main/resources/application.properties):

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/bank_db
spring.datasource.username=root
spring.datasource.password=
server.port=8080
```

For a password-protected MySQL user, copy
[`secrets.properties.example`](secrets.properties.example) to
`secrets.properties` in the project root and set the credentials:

```properties
spring.datasource.username=root
spring.datasource.password=your-password
```

`secrets.properties` is ignored by Git and must not be committed.

## Running the Application

1. Start MySQL.
2. From the project root, run:

   ```bash
   mvn spring-boot:run
   ```

   On Windows, the equivalent command is:

   ```powershell
   mvn.cmd spring-boot:run
   ```

3. Open [http://localhost:8080](http://localhost:8080).

On an empty database, the startup data loader creates:

| Account | Customer | Email | Opening balance |
|---|---|---|---:|
| `100001` | Kamal | kamal@gmail.com | LKR 50,000.00 |
| `100002` | Nimali | nimali@gmail.com | LKR 25,000.00 |
| `100003` | Saman | saman@gmail.com | LKR 10,000.00 |

The seed data is inserted only when no customers exist.

## User Interface Routes

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/` | Dashboard, customer search, statistics, transfer form, and latest transactions |
| `POST` | `/customers` | Create a customer |
| `POST` | `/transfer` | Transfer money between accounts |
| `GET` | `/accounts/{accountNumber}/history` | View account details and transaction history |
| `GET` | `/deposit` | Display the deposit form |
| `POST` | `/deposit` | Deposit funds into an account |
| `GET` | `/withdraw` | Display the withdrawal form |
| `POST` | `/withdraw` | Withdraw funds from an account |
| `GET` | `/large-transfers?min=10000` | List successful transactions at or above the minimum amount |

The application currently exposes HTML pages and form endpoints; it does not
provide a JSON REST API.

## Transaction Behavior

`BankService` uses Spring transactions for all money-changing operations:

1. Validate the amount and account rules.
2. Debit the sender using a guarded update that requires sufficient balance.
3. Optionally throw a simulated failure.
4. Credit the receiver.
5. Save a successful transaction record.

If any transfer step fails, the account changes and successful transaction
record are rolled back. The controller then calls
`TransactionAuditService.recordFailure`, which uses
`REQUIRES_NEW` so the `FAILED` audit record is committed independently of the
rolled-back transfer.

Deposits and withdrawals also use transactional guarded updates. Amounts must
be greater than zero, and withdrawals cannot exceed the current balance.
Transfers debit and credit the exact requested amount; no transfer fee is
applied by the service layer.

## Data Model

### `customers`

- `id` - generated primary key
- `account_number` - required and unique
- `full_name` - required
- `email` - required and unique
- `balance` - non-negative decimal balance

### `bank_transactions`

- `id` - generated primary key
- `from_account` - nullable for deposits
- `to_account` - nullable for withdrawals
- `amount`
- `status` - `SUCCESS` or `FAILED`
- `description`
- `created_at`

Hibernate uses `spring.jpa.hibernate.ddl-auto=update` to update the schema in
development environments.

## Project Structure

```text
src/
├── main/
│   ├── java/com/example/bankapp/
│   │   ├── config/          # Startup sample-data loader
│   │   ├── controller/      # Web routes and form handling
│   │   ├── dto/             # Form request objects
│   │   ├── exception/       # Banking domain exceptions
│   │   ├── model/           # JPA entities and enums
│   │   ├── repository/      # Spring Data JPA queries and updates
│   │   ├── service/         # Banking transactions and failure auditing
│   │   └── BankTransferApplication.java
│   └── resources/
│       ├── templates/       # Thymeleaf pages
│       ├── static/css/      # Application stylesheet
│       └── application.properties
└── test/
    └── java/                # Spring application tests
```

## Testing

Run the test suite with:

```bash
mvn test
```

The current test suite verifies that the Spring application context loads
successfully. A running MySQL instance and valid datasource configuration are
required for the full Spring Boot test context.

## Building a JAR

```bash
mvn clean package
java -jar target/bank-transfer-app-0.0.1-SNAPSHOT.jar
```

The packaged application still requires access to the configured MySQL
database.

## Development Notes

- SQL and transaction logging are enabled in `application.properties` to make
  transaction behavior visible during development.
- Thymeleaf caching is disabled for easier template iteration.
- The project uses `BigDecimal` for monetary values and stores balances and
  transaction amounts with two decimal places.
- Do not commit `secrets.properties` or other credentials.
