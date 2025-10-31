package lk.jiat.ee.globemed;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import lk.jiat.ee.globemed.model.MySQLConnection;
import javafx.geometry.Insets;

import java.sql.*;
import java.util.*;
import java.util.List;

//Composite Pattern
abstract class PermissionComponent {
    protected String name;
    public String getName() { return name; }
    public void add(PermissionComponent c) { throw new UnsupportedOperationException(); }
    public void remove(PermissionComponent c) { throw new UnsupportedOperationException(); }
    public List<PermissionComponent> getChildren() { return Collections.emptyList(); }
    public abstract void display();
}

class PermissionLeaf extends PermissionComponent {
    private int id;
    public PermissionLeaf(int id, String name) {
        this.id = id; this.name = name;
    }
    public int getId() { return id; }
    @Override public void display() {
        System.out.println(" - Permission: " + name);
    }
}

class PermissionCategoryComposite extends PermissionComponent {
    private List<PermissionComponent> children = new ArrayList<>();
    public PermissionCategoryComposite(String name) { this.name = name; }
    @Override public void add(PermissionComponent c) { children.add(c); }
    @Override public List<PermissionComponent> getChildren() { return children; }
    @Override public void display() {
        System.out.println("Category: " + name);
        for (PermissionComponent c : children) c.display();
    }
}

//Controller
public class ManagingRoles {
    @FXML private TextField txtRoleName;
    @FXML private ComboBox<String> cmbRoleCategory;
    @FXML private TextArea txtRoleDescription;
    @FXML private ListView<String> listRoles;
    @FXML private ComboBox<String> cmbSelectedRole;
    @FXML private VBox permissionsContainer;
    @FXML private Button btnCreateRole, btnSavePermissions;

    private Map<String,Integer> roleMap = new HashMap<>(); // role_name -> role_id
    private Map<String,Integer> permissionMap = new HashMap<>(); // checkbox text -> permission_id
    private Map<CheckBox,Integer> checkBoxPermission = new HashMap<>();

    @FXML
    public void initialize() {
        loadRoles();
        loadPermissionCategories();

        btnCreateRole.setOnAction(e -> onCreateRole());
        btnSavePermissions.setOnAction(e -> onSavePermissions());
    }

    private void loadRoles() {
        listRoles.getItems().clear();
        cmbSelectedRole.getItems().clear();
        roleMap.clear();
        try {
            ResultSet rs = MySQLConnection.execute("SELECT role_id, role_name FROM role");
            while (rs != null && rs.next()) {
                String name = rs.getString("role_name");
                int id = rs.getInt("role_id");
                roleMap.put(name, id);
                listRoles.getItems().add(name);
                cmbSelectedRole.getItems().add(name);
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void loadPermissionCategories() {
        permissionsContainer.getChildren().clear();
        permissionMap.clear();
        checkBoxPermission.clear();

        try {
            ResultSet rsCat = MySQLConnection.execute("SELECT * FROM permission_category");
            while (rsCat != null && rsCat.next()) {
                int catId = rsCat.getInt("category_id");
                String catName = rsCat.getString("category_name");

                TitledPane pane = new TitledPane();
                pane.setText(catName);

                GridPane grid = new GridPane();
                grid.setHgap(20);
                grid.setVgap(15);
                grid.setPadding(new Insets(10));

                ResultSet rsP = MySQLConnection.execute(
                        "SELECT * FROM permission WHERE permission_category_id=" + catId);

                int col = 0, row = 0;
                while (rsP != null && rsP.next()) {
                    int pid = rsP.getInt("permission_id");
                    String pname = rsP.getString("permission");
                    permissionMap.put(pname, pid);

                    CheckBox chk = new CheckBox(pname);
                    chk.setStyle(
                            "-fx-font-size: 14px;" +
                                    "-fx-text-fill: #1f2937;" +
                                    "-fx-background-color: #f1f5f9;" +
                                    "-fx-padding: 5 10 5 10;" +
                                    "-fx-background-radius: 8;"
                    );

                    checkBoxPermission.put(chk, pid);

                    grid.add(chk, col, row);

                    col++;
                    if (col > 1) {
                        col = 0;
                        row++;
                    }
                }

                pane.setContent(grid);
                permissionsContainer.getChildren().add(pane);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    private void onCreateRole() {
        String name = txtRoleName.getText();
        if (name.isEmpty()) { alert("Role name required!"); return; }
        try {
            MySQLConnection.execute(
                    "INSERT INTO role(role_name) VALUES('" + name + "')");
            loadRoles();
            alert("Role created successfully!");
            onClear();
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    @FXML
    private void onClear() {
        txtRoleName.clear();
        cmbRoleCategory.getSelectionModel().clearSelection();
        txtRoleDescription.clear();
    }

    @FXML
    private void onSavePermissions() {
        String role = cmbSelectedRole.getValue();
        if (role == null) { alert("Select role first!"); return; }
        int roleId = roleMap.get(role);

        try {
            MySQLConnection.execute("DELETE FROM role_permission WHERE role_id=" + roleId);

            for (Map.Entry<CheckBox,Integer> entry : checkBoxPermission.entrySet()) {
                if (entry.getKey().isSelected()) {
                    MySQLConnection.execute("INSERT INTO role_permission(role_id, permission_id) VALUES("
                            + roleId + "," + entry.getValue() + ")");
                }
            }
            alert("Permissions saved for role: " + role);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.showAndWait();
    }


    // === Usage Example ===
//    public class Composite {
//        public static void main(String[] args) {
//            //Category "Patient Management"
//            PermissionCategoryComposite patientCat = new PermissionCategoryComposite("Patient Management");
//            patientCat.add(new PermissionLeaf(1, "View Patients"));
//            patientCat.add(new PermissionLeaf(2, "Add Patient"));
//            patientCat.add(new PermissionLeaf(3, "Update Patient"));
//
//            //Category "Billing"
//            PermissionCategoryComposite billingCat = new PermissionCategoryComposite("Billing");
//            billingCat.add(new PermissionLeaf(4, "Generate Bill"));
//            billingCat.add(new PermissionLeaf(5, "Process Payment"));
//
//            //All categories
//            PermissionCategoryComposite root = new PermissionCategoryComposite("System Permissions");
//            root.add(patientCat);
//            root.add(billingCat);
//
//            // Display complete structure
//            root.display();
//        }
//    }


}
