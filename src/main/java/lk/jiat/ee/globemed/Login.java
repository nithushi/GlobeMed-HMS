package lk.jiat.ee.globemed;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lk.jiat.ee.globemed.model.MySQLConnection;
import java.sql.ResultSet;

//Login Service & Compound Pattern
interface LoginService {
    boolean authenticate(String username, String password) throws Exception;
}

class CoreLoginService implements LoginService {
    @Override
    public boolean authenticate(String username, String password) throws Exception {
        ResultSet rs = MySQLConnection.execute(
                "SELECT * FROM staff WHERE username='" + username + "' AND password='" + password + "'"
        );
        return rs != null && rs.next();
    }
}

class SecureLoginDecorator implements LoginService {
    private final LoginService wrappee;

    public SecureLoginDecorator(LoginService service) {
        this.wrappee = service;
    }

    @Override
    public boolean authenticate(String username, String password) throws Exception {
        password = encryptPassword(password);
        boolean result = wrappee.authenticate(username, password);
        logAttempt(username, result);
        return result;
    }

    private String encryptPassword(String password) {
        return password;
    }

    private void logAttempt(String username, boolean success) {
        System.out.println("Login attempt for " + username + " success: " + success);
    }
}

//Controller
public class Login {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label statusLabel;

    @FXML
    private void login() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Username cannot be empty");
            return;
        }
        if (password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Password cannot be empty");
            return;
        }

        try {
            LoginService service = new SecureLoginDecorator(new CoreLoginService());
            if (service.authenticate(username, password)) {
                ResultSet rs = MySQLConnection.execute(
                        "SELECT s.first_name, s.last_name, r.role_name " +
                                "FROM staff s INNER JOIN role r ON s.role_id = r.role_id " +
                                "WHERE s.username='" + username + "'"
                );
                String fullName = username; // fallback
                String role = "Guest";

                if (rs != null && rs.next()) {
                    fullName = rs.getString("first_name") + " " + rs.getString("last_name");
                    role = rs.getString("role_name");
                }
                openDashboard(fullName, role);
            } else {
                showAlert(Alert.AlertType.WARNING, "Warning", "Username or password doesn't match");
                usernameField.clear();
                passwordField.clear();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Login failed due to internal error");
        }
    }

    private void openDashboard(String fullName, String role) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("dashboard.fxml"));
        Parent root = loader.load();
        Dashboard controller = loader.getController();
        controller.setUserFullName(fullName);
        controller.setUserRole(role);
        Stage stage = (Stage) loginButton.getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.setTitle("Dashboard  Accessed By: " + fullName);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

