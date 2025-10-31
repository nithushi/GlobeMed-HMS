# 🩺 GlobeMed - Hospital Management System

**GlobeMed** is a modern, feature-rich desktop application for complete hospital management, built using **JavaFX**. It provides a clean, user-friendly interface for staff to manage patients, schedule appointments, handle billing, and control system permissions.

This project is a deep dive into building robust, real-world applications and serves as a practical showcase for advanced **Object-Oriented Design Patterns**.

---

## 🚀 Features

* **🔐 Secure Authentication**: Role-based login for staff (Doctors, Nurses, Admins) using a **Decorator Pattern** for secure handling.
* **👥 Patient Records**: Full CRUD (Create, Read, Update, Delete) functionality for patient information and medical history.
* **📅 Smart Scheduling**: An advanced appointment scheduler using the **Mediator Pattern** to manage complex dependencies between doctors, departments, dates, and available time slots.
* **💳 Billing & Claims**:
    * Generate and print beautiful PDF bills for patients.
    * Handle multiple payment types (Cash, Card, Insurance) using the **Bridge Pattern**.
    * Manage multi-step insurance claim approvals using a **Chain of Responsibility**.
* **🛡️ Role & Permission Control**:
    * Create custom staff roles (e.g., Admin, Doctor).
    * Assign granular permissions (e.g., "Can delete patient") using a **Composite Pattern** to group simple and complex permissions.
    * Enforce permissions using a **Chain of Responsibility** (`RoleHandler`).
* **📊 PDF Report Generation**: Dynamically generate PDF medical reports using the **Visitor Pattern**.

---

## 🛠️ Tech Stack & Architecture

* **Language**: **Java 17**
* **Framework**: **JavaFX** (for the modern UI)
* **Database**: **MySQL**
* **Build Tool**: **Apache Maven**
* **UI/UX**: FXML (`.fxml`) for layout design, with CSS styling.
* **Architecture**: This project is built around **powerful Design Patterns** to ensure it is **Decoupled, Flexible, and Scalable**.
    * **Decorator** (for `Login`)
    * **Mediator** (for `AppointmentSchedule`)
    * **Chain of Responsibility** (for `Billing` claims & `PatientView` permissions)
    * **Bridge** (for `Billing` payments)
    * **Composite** (for `ManagingRoles`)
    * **Visitor** (for `Reports`)

---

## 📁 Project Structure

```

GlobeMed-HMS/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── lk/jiat/ee/globemed/
│   │   │   │   ├── model/         \# Data models (Gender, Staff, etc.)
│   │   │   │   ├── MainApplication.java \# Entry point
│   │   │   │   ├── Login.java         \# Controllers
│   │   │   │   ├── Dashboard.java
│   │   │   │   ├── PatientView.java
│   │   │   │   ├── AppointmentSchedule.java
│   │   │   │   ├── Billing.java
│   │   │   │   ├── ManagingRoles.java
│   │   │   │   └── Reports.java
│   │   │   └── module-info.java
│   │   └── resources/
│   │       └── lk/jiat/ee/globemed/
│   │           ├── login.fxml       \# FXML Layouts
│   │           ├── dashboard.fxml
│   │           ├── patient\_view.fxml
│   │           └── ... (all other .fxml files)
├── .gitignore
├── pom.xml          \# Maven dependencies and config
└── README.md

````

---

## 🧑‍💻 Getting Started

### Prerequisites

* **Java Development Kit (JDK) 17** or higher
* **Apache Maven**
* **MySQL Server** (running on `localhost:3306`)

### 1. Clone the Repository

```bash
git clone [https://github.com/nithushi/GlobeMed-HMS.git](https://github.com/nithushi/GlobeMed-HMS.git)
cd GlobeMed-HMS
````

### 2\. Configure the Database

1.  Start your MySQL server.
2.  Create a new database named `globemed_db`.
    ```sql
    CREATE DATABASE globemed_db;
    ```
3.  **Import the Database**: You will need to create the tables (`patient`, `staff`, `role`, `appointment`, `gender`, etc.) based on the SQL queries found in the `.java` files.
4.  **Update Credentials**: Open the database connection file:
      * `src/main/java/lk/jiat/ee/globemed/model/MySQLConnection.java`
      * Update the username and password to match your MySQL setup.
      * **Default User**: `username`
      * **Default Password**: `******`

### 3\. Build & Run

1.  Open the project in your favorite Java IDE (like **IntelliJ IDEA**).

2.  Wait for Maven to download all dependencies (defined in `pom.xml`).

3.  Run the application using the Maven JavaFX plugin. Open a terminal in the project root and run:

    ```sh
    mvn clean javafx:run
    ```

    *(This command is configured in the `pom.xml` file)*

4.  The application will launch, starting with the **Login** screen.

-----

## 📦 Key Dependencies

  * `org.openjfx:javafx-controls`: Core JavaFX UI controls.
  * `org.openjfx:javafx-fxml`: For loading the FXML UI layouts.
  * `mysql:mysql-connector-java`: MySQL database driver.
  * `com.github.librepdf:openpdf`: Library for creating PDF documents.
  * `org.openjfx:javafx-maven-plugin`: To easily run the application.

-----

## 🤝 Contributing

Pull requests are welcome\! For major changes, please open an issue first to discuss what you would like to change.

-----

## ✨ Credits

Developed by **M. Nithushi Shavindi**
