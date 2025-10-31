package lk.jiat.ee.globemed;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lk.jiat.ee.globemed.model.Gender;
import lk.jiat.ee.globemed.model.MySQLConnection;

import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Base64;
import java.util.ResourceBundle;

public class PatientView implements Initializable {

    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private DatePicker dobPicker;
    @FXML private ComboBox<Gender> genderChoice;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextArea historyField;

    @FXML private TableView<Patient> patientTable;
    @FXML private TableColumn<Patient, Integer> colId;
    @FXML private TableColumn<Patient, String> colFirstName;
    @FXML private TableColumn<Patient, String> colLastName;
    @FXML private TableColumn<Patient, LocalDate> colDob;
    @FXML private TableColumn<Patient, String> colGender;
    @FXML private TableColumn<Patient, String> colPhone;
    @FXML private TableColumn<Patient, String> colEmail;
    @FXML private TableColumn<Patient, String> colHistory;

    private final ObservableList<Patient> patients = FXCollections.observableArrayList();

    private String currentUserRole;
    private boolean authenticated = true;
    private Integer selectedId = null;

    public void initialize(URL url, ResourceBundle rb) {

        loadGenders();

        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colFirstName.setCellValueFactory(new PropertyValueFactory<>("firstName"));
        colLastName.setCellValueFactory(new PropertyValueFactory<>("lastName"));
        colDob.setCellValueFactory(new PropertyValueFactory<>("dob"));
        colGender.setCellValueFactory(new PropertyValueFactory<>("gender"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colHistory.setCellValueFactory(new PropertyValueFactory<>("history"));

        patientTable.setItems(patients);

        patientTable.setOnMouseClicked(e -> {
            Patient p = patientTable.getSelectionModel().getSelectedItem();
            if (p != null) {
                selectedId = p.getId();
                firstNameField.setText(p.getFirstName());
                lastNameField.setText(p.getLastName());
                dobPicker.setValue(p.getDob());
                genderChoice.setValue(p.getGender());
                phoneField.setText(p.getPhone());
                emailField.setText(p.getEmail());
                historyField.setText(p.getHistory());
            }
        });

        //loadPatients();
    }

    private void loadGenders() {
        try {
            ResultSet rs = MySQLConnection.execute("SELECT * FROM gender");
            ObservableList<Gender> genders = FXCollections.observableArrayList();

            while (rs.next()) {
                //genders.add(new Gender(rs.getInt("gender_id"), rs.getString("gender_name")));
                int id = rs.getInt("gender_id");
                String name = rs.getString("gender_name");
                genders.add(new Gender(id, name));
            }

            genderChoice.setItems(genders);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onAddPatient(ActionEvent e) {
        Patient patient = readForm(null);
        if (patient == null) return;

        PatientRequest req = new PatientRequest(currentUserRole, authenticated, Action.ADD, patient);
        if (!HandlerChainBuilder.buildChain().handle(req)) return;

        String query = String.format(
                "INSERT INTO patient (first_name,last_name,dob,gender_id,contact,email,medical_history) " +
                        "VALUES ('%s','%s','%s','%s','%s','%s','%s')",
                patient.getFirstName(), patient.getLastName(),
                patient.getDob()==null? null : Date.valueOf(patient.getDob()),
                patient.getGender().getGender_id(), patient.getPhone(), patient.getEmail(),
                patient.getHistory()
        );
        MySQLConnection.execute(query);
        loadPatients();
        clearForm();
    }

    @FXML
    private void onUpdatePatient(ActionEvent e) {
        if (selectedId == null) {
            alert("Select a record first.", Alert.AlertType.WARNING);
            return;
        }
        Patient patient = readForm(selectedId);
        if (patient == null) return;

        PatientRequest req = new PatientRequest(currentUserRole, authenticated, Action.UPDATE, patient);
        if (!HandlerChainBuilder.buildChain().handle(req)) return;

        String query = String.format(
                "UPDATE patient SET first_name='%s', last_name='%s', dob='%s', gender_id='%s', contact='%s', email='%s', medical_history='%s' WHERE patient_id=%d",
                patient.getFirstName(), patient.getLastName(),
                patient.getDob()==null? null : Date.valueOf(patient.getDob()),
                patient.getGender().getGender_id(), patient.getPhone(), patient.getEmail(),
                patient.getHistory(), patient.getId()
        );
        MySQLConnection.execute(query);
        loadPatients();
        clearForm();
    }

    @FXML
    private void onDeletePatient(ActionEvent e) {
        if (selectedId == null) {
            alert("Select a record first.", Alert.AlertType.WARNING);
            return;
        }
        Patient payload = new Patient(selectedId,null,null,null,null,null,null,null);

        PatientRequest req = new PatientRequest(currentUserRole, authenticated, Action.DELETE, payload);
        if (!HandlerChainBuilder.buildChain().handle(req)) return;

        String query = "DELETE FROM patient WHERE patient_id=" + selectedId;
        MySQLConnection.execute(query);
        loadPatients();
        clearForm();
    }

    @FXML
    private void onClearForm(ActionEvent e) {
        clearForm();
    }

    public void loadPatients() {
        patients.clear();
        PatientRequest req = new PatientRequest(currentUserRole, authenticated, Action.VIEW, null);
        if (!HandlerChainBuilder.buildChain().handle(req)) return;

        try {
            // JOIN query to get gender_name
            ResultSet rs = MySQLConnection.execute(
                    "SELECT p.*, g.gender_name " +
                            "FROM patient p LEFT JOIN gender g ON p.gender_id = g.gender_id " +
                            "ORDER BY p.patient_id DESC"
            );
            while (rs != null && rs.next()) {
                patients.add(new Patient(
                        rs.getInt("patient_id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getDate("dob")==null? null: rs.getDate("dob").toLocalDate(),
                        new Gender(rs.getInt("gender_id"), rs.getString("gender_name")),
                        rs.getString("contact"),
                        rs.getString("email"),
                        rs.getString("medical_history")
                ));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private Patient readForm(Integer id) {
        String fn = firstNameField.getText().trim();
        String ln = lastNameField.getText().trim();
        LocalDate dob = dobPicker.getValue();
        String phone = phoneField.getText().trim();
        String email = emailField.getText().trim();
        String history = historyField.getText();
        Gender gender = genderChoice.getValue();

        if (fn.isEmpty() || ln.isEmpty()) {
            alert("First and Last name required.", Alert.AlertType.WARNING);
            return null;
        }
        return new Patient(id, fn, ln, dob, gender, phone, email, history);
    }

    private void clearForm() {
        selectedId = null;
        firstNameField.clear(); lastNameField.clear();
        dobPicker.setValue(null);
        genderChoice.setValue(null);
        phoneField.clear(); emailField.clear();
        historyField.clear();
        patientTable.getSelectionModel().clearSelection();
    }

    private void alert(String msg, Alert.AlertType type) {
        Alert a = new Alert(type);
        a.setContentText(msg);
        a.show();
    }


    //Model
    public static class Patient {
        private final Integer id;
        private final String firstName, lastName,phone, email, history;
        private final LocalDate dob;
        private final Gender gender;


        public Patient(Integer id, String firstName, String lastName, LocalDate dob,
                       Gender gender, String phone, String email, String history) {
            this.id=id; this.firstName=firstName; this.lastName=lastName;
            this.dob=dob; this.gender=gender; this.phone=phone; this.email=email; this.history=history;
        }
        public Integer getId() { return id; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public LocalDate getDob() { return dob; }
        public Gender getGender() { return gender; }
        public String getPhone() { return phone; }
        public String getEmail() { return email; }
        public String getHistory() { return history; }
    }


    //SECURITY (Chain of Responsibility Pattern)
    public void setCurrentUserRole(String role) {
        this.currentUserRole = role;
    }

    enum Action { VIEW, ADD, UPDATE, DELETE }

    static class PatientRequest {
        String role; boolean authenticated; Action action; Patient payload;
        PatientRequest(String r, boolean a, Action act, Patient p) {
            role=r; authenticated=a; action=act; payload=p;
        }
    }

    static abstract class Handler {
        Handler next;
        Handler setNext(Handler n){ this.next=n; return n; }
        final boolean handle(PatientRequest req){
            if(!process(req)) return false;
            return next==null || next.handle(req);
        }
        protected abstract boolean process(PatientRequest req);
    }

    static class AuthHandler extends Handler {
        @Override protected boolean process(PatientRequest req) {
            if(!req.authenticated){ System.out.println("Not authenticated"); return false; }
            return true;
        }
    }

    static class RoleHandler extends Handler {
        @Override
        protected boolean process(PatientRequest req) {
            boolean allowed = false;
            switch (req.action) {
                case VIEW:
                    allowed = req.role.equalsIgnoreCase("Doctor")
                            || req.role.equalsIgnoreCase("Nurse")
                            || req.role.equalsIgnoreCase("Admin");
                    break;
                case ADD:
                case UPDATE:
                    allowed = req.role.equalsIgnoreCase("Doctor")
                            || req.role.equalsIgnoreCase("Admin");
                    break;
                case DELETE:
                    allowed = req.role.equalsIgnoreCase("Admin");
                    break;
            }

            if (!allowed) {
                System.out.println("Access denied: " + req.role + " cannot " + req.action);
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setTitle("Permission Denied");
                a.setHeaderText(null);
                a.setContentText("Access denied: " + req.role + " cannot " + req.action);
                a.showAndWait();
                return false;
            }

            System.out.println("" + req.role + " has permission for " + req.action);
            return true;
        }
    }

    static class AuditHandler extends Handler {
        @Override protected boolean process(PatientRequest req) {
            if (req != null && req.role != null) {
                System.out.println("[AUDIT] "+req.role+" did "+req.action);
            }
            return true;
        }
    }
    static class HandlerChainBuilder {
        static Handler buildChain() {
            //return new AuthHandler().setNext(new RoleHandler()).setNext(new EncryptHandler()).setNext(new AuditHandler());
            AuthHandler auth = new AuthHandler();
            RoleHandler role = new RoleHandler();
            AuditHandler audit = new AuditHandler();

            auth.setNext(role).setNext(audit);
            return auth;
        }
    }

}