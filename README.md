# GlobeMed 🩺 - Advanced Hospital Management System

**A comprehensive, JavaFX-based desktop application for modern healthcare management.**

GlobeMed is a feature-rich Hospital Management System (HMS) built with **JavaFX** and **Maven**. It provides a robust, secure, and user-friendly platform for managing all core hospital operations, from patient registration to complex insurance billing.

This project isn't just a simple CRUD application; it's a practical showcase of advanced object-oriented design patterns to solve real-world problems in a clean, scalable, and maintainable way.

## ✨ Core Features

* **🔐 Secure Authentication:** A secure login system for all staff members.
* **👥 Patient Management:** Full CRUD (Create, Read, Update, Delete) capabilities for all patient records, including medical history.
* **📅 Smart Appointment Scheduling:** An intuitive interface to book and manage patient appointments with specific doctors, departments, and time slots.
* **💳 Billing & Insurance:** A powerful billing module to generate invoices, process payments (Cash, Card, Insurance), and manage complex, multi-step insurance claim approval workflows.
* **🛡️ Role & Permission Control:** A granular system to create staff roles and assign specific permissions, ensuring employees can only access the modules they are authorized for.
* **📄 Dynamic PDF Reporting:** Generate and open beautiful, print-ready PDF reports for patient bills and medical summaries.

## 🛠️ Tech Stack

* **Core:** Java 17
* **Framework:** JavaFX (for the modern desktop UI)
* **Build Tool:** Apache Maven
* **Database:** MySQL
* **Libraries:**
    * `mysql-connector-java`: For database connectivity.
    * `com.github.librepdf:openpdf`: For generating PDF documents.

## 🎓 A Showcase of Design Patterns

This project was built with a strong emphasis on clean architecture. It leverages several key design patterns to promote decoupling, flexibility, and maintainability.

* **Decorator Pattern:** Used in `Login.java` to add security features (like logging or encryption) to the core authentication service without modifying it.
* **Mediator Pattern:** Implemented in `AppointmentSchedule.java` to manage the complex communication and dependencies between multiple UI controls (Department, Doctor, Date, Time Slot).
* **Chain of Responsibility:**
    * Used in `PatientView.java` to create an authorization chain (`AuthHandler`, `RoleHandler`) that checks if a user has the correct permissions for an action (View, Add, Update, Delete).
    * Used in `Billing.java` to model the multi-step insurance claim approval process (`SubmitHandler`, `ManagerReviewHandler`, etc.).
* **Bridge Pattern:** Implemented in `Billing.java` to decouple the high-level `BillingService` from the concrete payment implementations (`CashProcessor`, `CardProcessor`, `InsuranceProcessor`).
* **Composite Pattern:** Used in `ManagingRoles.java` to treat individual permissions (`PermissionLeaf`) and groups of permissions (`PermissionCategoryComposite`) uniformly.
* **Visitor Pattern:** Implemented in `Reports.java` to allow new types of reports (like PDF reports) to be generated from the patient data structure without modifying the data classes themselves.

## 🚀 Getting Started

Follow these instructions to get a copy of the project up and running on your local machine.

### Prerequisites

* **Java Development Kit (JDK) 17** or higher.
* **Apache Maven**
* **MySQL Server** (running on `localhost:3306`)

### 1. Database Setup

1.  Start your MySQL server.
2.  Create a new database named `globemed_db`.
    ```sql
    CREATE DATABASE globemed_db;
    ```
3.  **Important:** You must manually create the tables required by the application (e.g., `staff`, `role`, `patient`, `appointment`, `billing`, etc.) by examining the `.java` files in the `lk.jiat.ee.globemed.model` and other packages.
4.  Configure your database credentials in the `MySQLConnection.java` file:
    * **File:** `src/main/java/lk/jiat/ee/globemed/model/MySQLConnection.java`
    * **Default User:** `username`
    * **Default Password:** `********`
    * Update these values to match your local MySQL setup.

### 2. Build and Run the Application

1.  Open a terminal in the project's root directory (where `pom.xml` is located).
2.  Install all dependencies using Maven:
    ```sh
    mvn clean install
    ```
3.  Run the application using the JavaFX Maven plugin:
    ```sh
    mvn clean javafx:run
    ```
