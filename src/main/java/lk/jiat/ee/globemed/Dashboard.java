package lk.jiat.ee.globemed;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Button;

import java.io.IOException;

public class Dashboard {

    @FXML private StackPane contentArea;
    @FXML private Button patientsBtn;
    @FXML private Label userNameLabel;
    @FXML private Label userRoleLabel;
    @FXML private Label userLabel;

    private String role;

    @FXML
    private void initialize() {
    }

    public void setUserFullName(String fullName) {
        userNameLabel.setText(fullName);
        userLabel.setText("Dr. " + fullName);
    }

    public void setUserRole(String role) {
        this.role = role;
        userRoleLabel.setText(role);
    }

    @FXML
    private void handlePatients() {
        loadView("patient_view.fxml");
    }

    @FXML
    private void handleAppointments() {
        loadView("appointment_schedule.fxml");
    }

    @FXML
    private void handleBilling() {
        loadView("billing.fxml");
    }

    @FXML
    private void handleRoles() {
        loadView("managing_roles.fxml");
    }

    @FXML
    private void handleReports(){
        loadView("reports.fxml");
    }


    // Dashboard
    private void loadView(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/lk/jiat/ee/globemed/" + fxml));
            Parent root = loader.load();
            Object controller = loader.getController();

            boolean accessAllowed = true;

            // Role check for PatientView
            if (controller instanceof PatientView patientController) {
                patientController.setCurrentUserRole(this.role);
                PatientView.PatientRequest req = new PatientView.PatientRequest(this.role, true, PatientView.Action.VIEW, null);
                accessAllowed = PatientView.HandlerChainBuilder.buildChain().handle(req);
                if (accessAllowed) patientController.loadPatients();
            }

            // Role check for AppointmentView (example)
            if (controller instanceof AppointmentSchedule appointmentController) {
                accessAllowed = checkRoleForAppointments(this.role);
            } else if (controller instanceof Billing billingController) {
                accessAllowed = checkRoleForAppointments(this.role);
            } else if (controller instanceof Reports reportsController) {
                accessAllowed = checkRoleForAppointments(this.role);
            } else if (controller instanceof ManagingRoles managingRolesController) {
                accessAllowed = checkRoleForAppointments(this.role);
            }

            if (!accessAllowed) return;

            contentArea.getChildren().setAll(root);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean checkRoleForAppointments(String role) {
        //only Doctor and Nurse and Admin can access appointments
        if (role.equalsIgnoreCase("Doctor") || role.equalsIgnoreCase("Nurse") || role.equalsIgnoreCase("Admin")) {
            return true;
        }
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Permission Denied");
        a.setHeaderText(null);
        a.setContentText("Access denied: " + role + " cannot access");
        a.showAndWait();
        return false;
    }

    @FXML
    private void loadDashboard() {

    }
}
