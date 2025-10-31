package lk.jiat.ee.globemed;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lk.jiat.ee.globemed.model.MySQLConnection;

import java.net.URL;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class AppointmentSchedule implements Initializable {

    @FXML private TextField patientNameField;
    @FXML private TextField contactField;
    @FXML private ComboBox<Department> facilityComboBox;
    @FXML private ComboBox<Doctor> specialistComboBox;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<String> timeSlotComboBox;
    @FXML private Label conflictLabel;

    @FXML private TableView<ScheduleVM> scheduleTable;
    @FXML private TableColumn<ScheduleVM, String> colDoctor;
    @FXML private TableColumn<ScheduleVM, String> colDate;
    @FXML private TableColumn<ScheduleVM, String> colTime;
    @FXML private TableColumn<ScheduleVM, String> colFacility;
    @FXML private TableColumn<ScheduleVM, String> colStatus;
    @FXML private TableColumn<ScheduleVM, Integer> colCapacity;


    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    //Mediator
    private SchedulingMediator mediator;

    //Mediator API
    interface SchedulingMediator {
        ObservableList<Department> loadDepartments();
        ObservableList<Doctor> loadDoctorsByDepartment(int deptId);
        ObservableList<String> loadSlotsForDoctor(Doctor doctor);
        int bookingsCountForSlot(int doctorId, LocalDateTime slot);
        boolean book(String patientName, String contact, int doctorId, LocalDateTime slot, Integer staffId, int statusId);
        ObservableList<ScheduleVM> loadSchedule(Integer doctorId, LocalDate forDate);
    }

    //Concrete
    private class AppointmentSchedulingMediator implements SchedulingMediator {

        @Override
        public ObservableList<Department> loadDepartments() {
            ObservableList<Department> list = FXCollections.observableArrayList();
            try (ResultSet rs = MySQLConnection.execute(
                    "SELECT department_id, department_name FROM department ORDER BY department_name")) {
                while (rs != null && rs.next()) {
                    list.add(new Department(rs.getInt("department_id"), rs.getString("department_name")));
                }
            } catch (Exception e) { e.printStackTrace(); }
            return list;
        }

        @Override
        public ObservableList<Doctor> loadDoctorsByDepartment(int deptId) {
            ObservableList<Doctor> list = FXCollections.observableArrayList();
            String q = "SELECT d.doctor_id, d.doctor_name, d.capacity, d.time_slots, dp.department_name " +
                    "FROM doctor d JOIN department dp ON d.department_id = dp.department_id " +
                    "WHERE d.department_id = " + deptId + " ORDER BY d.doctor_name";
            try (ResultSet rs = MySQLConnection.execute(q)) {
                while (rs != null && rs.next()) {
                    list.add(new Doctor(
                            rs.getInt("doctor_id"),
                            rs.getString("doctor_name"),
                            rs.getString("department_name"),
                            rs.getInt("capacity"),
                            rs.getString("time_slots")
                    ));
                }
            } catch (Exception e) { e.printStackTrace(); }
            return list;
        }

        @Override
        public ObservableList<String> loadSlotsForDoctor(Doctor doctor) {
            ObservableList<String> slots = FXCollections.observableArrayList();
            if (doctor == null || doctor.availableSlots == null) return slots;

            for (String s : doctor.availableSlots.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    // store as-is (yyyy-MM-dd HH:mm:ss)
                    slots.add(trimmed);
                }
            }
            return slots;
        }

        @Override
        public int bookingsCountForSlot(int doctorId, LocalDateTime slot) {
            String q = "SELECT COUNT(*) AS c FROM appointment " +
                    "WHERE doctor_id = " + doctorId + " AND date_time = '" + TS.format(slot) + "'";
            try (ResultSet rs = MySQLConnection.execute(q)) {
                if (rs != null && rs.next()) return rs.getInt("c");
            } catch (Exception e) { e.printStackTrace(); }
            return 0;
        }

        @Override
        public boolean book(String patientName, String contact, int doctorId, LocalDateTime slot,
                            Integer staffId, int statusId) {

            // sanitize single quotes minimally (since provided MySQL helper uses raw statements)
            String p = patientName.replace("'", "''");
            String c = contact.replace("'", "''");
            String staff = (staffId == null) ? "NULL" : String.valueOf(staffId);

            String q = "INSERT INTO appointment (patient_name, contact, date_time, staff_id, doctor_id, appointment_status) " +
                    "VALUES ('" + p + "', '" + c + "', '" + TS.format(slot) + "', " + 1 + ", " + doctorId + ", " + statusId + ")";
            try {
                MySQLConnection.execute(q); // returns null for non-SELECT; OK
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        public ObservableList<ScheduleVM> loadSchedule(Integer doctorId, LocalDate forDate) {
            ObservableList<ScheduleVM> list = FXCollections.observableArrayList();

            String where = " WHERE 1=1 ";
            if (doctorId != null) where += " AND a.doctor_id = " + doctorId + " ";
            if (forDate != null)  where += " AND DATE(a.date_time) = '" + forDate + "' ";

            String q = "SELECT a.appointment_id, d.doctor_name, dp.department_name, a.date_time, " +
                    "       a.appointment_status, d.capacity " +
                    "FROM appointment a " +
                    "JOIN doctor d ON a.doctor_id = d.doctor_id " +
                    "JOIN department dp ON d.department_id = dp.department_id " +
                    where +
                    "ORDER BY a.date_time DESC LIMIT 200";

            try (ResultSet rs = MySQLConnection.execute(q)) {
                while (rs != null && rs.next()) {
                    LocalDateTime t = rs.getTimestamp("date_time").toLocalDateTime();
                    String time = t.toLocalTime().toString();

                    // map status number to status name
                    int statusNum = rs.getInt("appointment_status");
                    String statusName;
                    switch (statusNum) {
                        case 1: statusName = "Scheduled";break;  // green
                        case 2: statusName = "Cancelled"; break;  // red
                        default: statusName = "Pending"; break;   // amber
                    }

                    list.add(new ScheduleVM(
                            rs.getInt("appointment_id"),
                            rs.getString("doctor_name"),
                            t.toLocalDate().toString(),
                            time,
                            rs.getString("department_name"),
                            //String.valueOf(rs.getInt("appointment_status")),
                            statusName,
                            rs.getInt("capacity")
                    ));
                }
            } catch (Exception e) { e.printStackTrace(); }

            return list;

        }
    }

    //Colleague
    public static class Department {
        public final int id;
        public final String name;
        public Department(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }

    public static class Doctor {
        public final int id;
        public final String name;
        public final String departmentName;
        public final int capacity; // max appointments per time slot
        public final String availableSlots;
        public Doctor(int id, String name, String departmentName, int capacity, String availableSlots) {
            this.id = id; this.name = name; this.departmentName = departmentName;
            this.capacity = capacity; this.availableSlots = availableSlots;
        }
        @Override public String toString() { return name + " (" + departmentName + ")"; }
    }

    public static class ScheduleVM {
        public final SimpleIntegerProperty id = new SimpleIntegerProperty();
        public final SimpleStringProperty doctor = new SimpleStringProperty();
        public final SimpleStringProperty date = new SimpleStringProperty();
        public final SimpleStringProperty time = new SimpleStringProperty();
        public final SimpleStringProperty facility = new SimpleStringProperty();
        public final SimpleStringProperty status = new SimpleStringProperty();
        public final SimpleIntegerProperty capacity = new SimpleIntegerProperty();

        public ScheduleVM(int id, String doctor, String date, String time,
                          String facility, String status, int capacity) {
            this.id.set(id);
            this.doctor.set(doctor);
            this.date.set(date);
            this.time.set(time);
            this.facility.set(facility);
            this.status.set(status);
            this.capacity.set(capacity);
        }

        public String getDoctor() { return doctor.get(); }
        public String getDate() { return date.get(); }
        public String getTime() { return time.get(); }
        public String getFacility() { return facility.get(); }
        public String getStatus() { return status.get(); }
        public int getCapacity() { return capacity.get(); }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        mediator = new AppointmentSchedulingMediator();

        // Load Departments
        facilityComboBox.setItems(mediator.loadDepartments());

        // When facility changes -> load doctors
        facilityComboBox.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            specialistComboBox.getItems().clear();
            timeSlotComboBox.getItems().clear();
            if (n != null) {
                specialistComboBox.setItems(mediator.loadDoctorsByDepartment(n.id));
                refreshSchedule(null, null); // broader view
            }
        });

        // When doctor changes -> load that doctor's time slots
        specialistComboBox.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            timeSlotComboBox.getItems().clear();
            if (n != null) {
                timeSlotComboBox.setItems(mediator.loadSlotsForDoctor(n));
                refreshSchedule(n.id, datePicker.getValue());
            }
        });

        // When date changes -> refresh schedule view
        datePicker.valueProperty().addListener((obs, o, n) -> {
            Doctor d = specialistComboBox.getValue();
            refreshSchedule(d == null ? null : d.id, n);
        });

        colDoctor.setCellValueFactory(new PropertyValueFactory<>("doctor"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));
        colFacility.setCellValueFactory(new PropertyValueFactory<>("facility"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colCapacity.setCellValueFactory(new PropertyValueFactory<>("capacity"));

        conflictLabel.setVisible(false);
    }

    private void refreshSchedule(Integer doctorId, LocalDate date) {
        ObservableList<ScheduleVM> scheduleList = mediator.loadSchedule(doctorId, date);
        scheduleTable.setItems(scheduleList);

        scheduleTable.setRowFactory(tv -> new TableRow<ScheduleVM>() {
            @Override
            protected void updateItem(ScheduleVM item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else {
                    switch (item.getStatus()) {
                        case "Scheduled": // Booked
                            setStyle("-fx-background-color: #d1fae5"); // light green
                            break;
                        case "Cancelled": // Cancelled
                            setStyle("-fx-background-color: #fee2e2"); // light red
                            break;
                        default: // Pending or others
                            setStyle("");
                            break;
                    }
                }
            }
        });
    }

    @FXML
    private void onCheckAvailability() {
        conflictLabel.setVisible(false);

        Doctor doc = specialistComboBox.getValue();
        LocalDate d = datePicker.getValue();
        String time = timeSlotComboBox.getValue();

        if (doc == null || d == null || time == null) {
            showWarn("Please select department, doctor, date and time.");
            return;
        }

        LocalDateTime slot;
        try {
            if (time.contains("-")) {
                // full datetime string from DB
                slot = LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else {
                // time only
                slot = LocalDateTime.of(d, LocalTime.parse(time));
            }
        } catch (Exception e) {
            showError("Invalid time format: " + time);
            return;
        }

        int booked = mediator.bookingsCountForSlot(doc.id, slot);

        if (booked >= doc.capacity) {
            showError("This slot is fully booked for " + doc.name + ". Capacity: " + doc.capacity);
        } else {
            showOk("Available   (" + booked + "/" + doc.capacity + " booked)");
        }
    }


    @FXML
    private void onBookAppointment() {
        conflictLabel.setVisible(false);

        String patient = safe(patientNameField.getText());
        String contact = safe(contactField.getText());
        Department dep = facilityComboBox.getValue();
        Doctor doc = specialistComboBox.getValue();
        LocalDate d = datePicker.getValue();
        String time = timeSlotComboBox.getValue();

        if (isEmpty(patient) || isEmpty(contact) || dep == null || doc == null || d == null || time == null) {
            showWarn("Please fill all fields to book.");
            return;
        }

        LocalDateTime slot;
        try {
            if (time.contains("-")) {
                //full datetime string from DB
                slot = LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else {
                //only time string from UI
                slot = LocalDateTime.of(d, LocalTime.parse(time));
            }
        } catch (Exception e) {
            showError("Invalid time format: " + time);
            return;
        }

        // capacity check
        int booked = mediator.bookingsCountForSlot(doc.id, slot);
        if (booked >= doc.capacity) {
            showError("Cannot book. Slot already full (" + booked + "/" + doc.capacity + ").");
            return;
        }

        // appointment_status: 1 = Booked (adjust to your seed data)
        boolean ok = mediator.book(patient, contact, doc.id, slot, 3, 1);
        if (ok) {
            showOk("Appointment booked successfully.");
            clearForm(false);
            refreshSchedule(doc.id, d);
            System.out.println("Refreshing schedule for doctorId=" + doc.id + " date=" + slot.toString());

        } else {
            showError("Error booking appointment. Try again.");
        }
    }


    @FXML
    private void onClearForm() {
        clearForm(true);
    }

    private void clearForm(boolean hideMsg) {
        patientNameField.clear();
        contactField.clear();
        datePicker.setValue(null);
        timeSlotComboBox.getItems().clear();
        specialistComboBox.getSelectionModel().clearSelection();
        if (hideMsg) conflictLabel.setVisible(false);
    }

    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }
    private static String safe(String s) { return s == null ? "" : s.trim(); }


    private void showWarn(String msg) {
        conflictLabel.setText("⚠ " + msg);
        conflictLabel.setStyle(
                "-fx-text-fill: #f59e0b;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 14px;" +
                        "-fx-background-color: #fff7ed;" +
                        "-fx-padding: 8px 12px;" +
                        "-fx-background-radius: 10px;" +
                        "-fx-border-color: #fbbf24;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 10px;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);"
        );
        conflictLabel.setVisible(true);
    }

    private void showError(String msg) {
        conflictLabel.setText("❌ " + msg);
        conflictLabel.setStyle(
                "-fx-text-fill: #dc2626;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 14px;" +
                        "-fx-background-color: #fee2e2;" +
                        "-fx-padding: 8px 12px;" +
                        "-fx-background-radius: 10px;" +
                        "-fx-border-color: #f87171;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 10px;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);"
        );
        conflictLabel.setVisible(true);
    }

    private void showOk(String msg) {
        conflictLabel.setText("✅ " + msg);
        conflictLabel.setStyle(
                "-fx-text-fill: #065f46;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 14px;" +
                        "-fx-background-color: #d1fae5;" +
                        "-fx-padding: 8px 12px;" +
                        "-fx-background-radius: 10px;" +
                        "-fx-border-color: #34d399;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 10px;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);"
        );
        conflictLabel.setVisible(true);
    }

}
