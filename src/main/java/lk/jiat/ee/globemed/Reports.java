package lk.jiat.ee.globemed;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lk.jiat.ee.globemed.model.MySQLConnection;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.*;
import java.util.*;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

//Visitor Pattern Interfaces
interface ReportVisitor {
    void visit(PatientElement patient, int reportTypeId, String reportType, String format) throws Exception;
}

interface ReportElement {
    void accept(ReportVisitor visitor, int reportTypeId, String reportType, String format) throws Exception;
}

class PatientElement implements ReportElement {
    private final int id;
    private final String fullName;

    public PatientElement(int id, String fullName) {
        this.id = id;
        this.fullName = fullName;
    }

    public int getId() { return id; }
    public String getFullName() { return fullName; }

    @Override
    public void accept(ReportVisitor visitor, int reportTypeId, String reportType, String format) throws Exception {
        visitor.visit(this, reportTypeId, reportType, format);
    }
}

//PDF Report Visitor Implementation
class PdfReportVisitor implements ReportVisitor {
    @Override
    public void visit(PatientElement patient, int reportTypeId, String reportType, String format) throws Exception {
        // === Save in reports/ folder ===
        File dir = new File("reports");
        if (!dir.exists()) dir.mkdirs();

        String filePath = "reports/Report_" + patient.getId() + "_" + System.currentTimeMillis() + ".pdf";

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(filePath));
        document.open();

        document.add(new Paragraph("GlobeMed Medical Report"));
        document.add(new Paragraph("Report Type: " + reportType));
        document.add(new Paragraph("Patient: " + patient.getFullName()));
        document.add(new Paragraph("Generated Date: " + new java.util.Date()));
        document.add(new Paragraph(" "));

        // Example Table
        PdfPTable table = new PdfPTable(2);
        table.addCell("Field");
        table.addCell("Value");
        table.addCell("Patient ID");
        table.addCell(String.valueOf(patient.getId()));
        table.addCell("Patient Name");
        table.addCell(patient.getFullName());
        document.add(table);

        document.close();

        // Insert record into DB with file path
        String sql = "INSERT INTO medical_report(patient_id, generated_date, content, report_type_id) " +
                "VALUES(" + patient.getId() + ", NOW(), '" + filePath + "', " + reportTypeId + ")";
        MySQLConnection.execute(sql);
    }
}

//Controller
public class Reports {

    @FXML private ComboBox<String> reportTypeComboBox;
    @FXML private DatePicker startDatePicker, endDatePicker;
    @FXML private TextField filterTextField;
    @FXML private ComboBox<String> formatComboBox;
    @FXML private Button generateButton, previewButton, clearButton;
    @FXML private TextArea statusTextArea;
    @FXML private ProgressBar progressBar;

    @FXML private TableView<ReportRecord> recentReportsTable;
    @FXML private TableColumn<ReportRecord, String> reportNameColumn, reportTypeColumn, generatedDateColumn, statusColumn, actionsColumn, reportPatientColumn;

    private final Map<String, Integer> reportTypeMap = new HashMap<>();

    @FXML
    public void initialize() {
        loadReportTypes();
        setupTable();

        generateButton.setOnAction(e -> generateReport());
        previewButton.setOnAction(e -> previewReport());
        clearButton.setOnAction(e -> clearForm());

        progressBar.setVisible(false);
    }

    private void loadReportTypes() {
        try {
            ResultSet rs = MySQLConnection.execute("SELECT * FROM report_type");
            while (rs != null && rs.next()) {
                String type = rs.getString("report_type");
                int id = rs.getInt("report_id");
                reportTypeComboBox.getItems().add(type);
                reportTypeMap.put(type, id);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupTable() {
        reportNameColumn.setCellValueFactory(new PropertyValueFactory<>("reportName"));
        reportTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        generatedDateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        actionsColumn.setCellValueFactory(new PropertyValueFactory<>("actions"));
        reportPatientColumn.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        loadRecentReports();
    }

    private void loadRecentReports() {
        recentReportsTable.getItems().clear();
        try {
            ResultSet rs = MySQLConnection.execute(
                    "SELECT mr.report_id, rt.report_type, mr.generated_date, mr.content, " +
                            "CONCAT(p.first_name, ' ', p.last_name) AS patient_name " +
                            "FROM medical_report mr " +
                            "JOIN report_type rt ON mr.report_type_id = rt.report_id " +
                            "JOIN patient p ON mr.patient_id = p.patient_id " +
                            "ORDER BY mr.generated_date DESC LIMIT 10"
            );

            while (rs != null && rs.next()) {
                String reportName = "Report #" + rs.getInt("report_id");
                String type = rs.getString("report_type");
                String date = rs.getString("generated_date");
                String status = "Generated";
                String actions = "Open";
                String filePath = rs.getString("content");
                String patientName = rs.getString("patient_name");

                ReportRecord record = new ReportRecord(reportName, type, date, status, actions, filePath, patientName);
                recentReportsTable.getItems().add(record);
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void generateReport() {
        String type = reportTypeComboBox.getValue();
        String format = formatComboBox.getValue();
        if (type == null || format == null) {
            showStatus("Select report type and format!", true);
            return;
        }

        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        try {
            ResultSet rs = MySQLConnection.execute("SELECT * FROM patient LIMIT 1");
            if (rs != null && rs.next()) {
                int pid = rs.getInt("patient_id");
                String fullName = rs.getString("first_name") + " " + rs.getString("last_name");
                PatientElement patient = new PatientElement(pid, fullName);

                ReportVisitor visitor = new PdfReportVisitor();
                patient.accept(visitor, reportTypeMap.get(type), type, format);

                showStatus("Report generated successfully for " + fullName, false);
                loadRecentReports();
            }
        } catch (Exception e) {
            e.printStackTrace();
            showStatus("Error generating report.", true);
        } finally {
            progressBar.setVisible(false);
        }
    }

    private void previewReport() {
        try {
            ResultSet rs = MySQLConnection.execute(
                    "SELECT content FROM medical_report ORDER BY report_id DESC LIMIT 1");
            if (rs != null && rs.next()) {
                String filePath = rs.getString("content");
                File pdf = new File(filePath);
                if (pdf.exists()) {
                    Desktop.getDesktop().open(pdf);
                    showStatus("Preview opened: " + filePath, false);
                } else {
                    showStatus("PDF file not found.", true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            showStatus("Error opening preview.", true);
        }
    }

    public class ReportRecord {
        private final String reportName;
        private final String type;
        private final String date;
        private final String status;
        private final String actions;
        private final String filePath;
        private final String patientName;

        public ReportRecord(String reportName, String type, String date, String status, String actions, String filePath, String patientName) {
            this.reportName = reportName;
            this.type = type;
            this.date = date;
            this.status = status;
            this.actions = actions;
            this.filePath = filePath;
            this.patientName = patientName;
        }

        public String getReportName() { return reportName; }
        public String getType() { return type; }
        public String getDate() { return date; }
        public String getStatus() { return status; }
        public String getActions() { return actions; }
        public String getFilePath() { return filePath; }
        public String getPatientName() { return patientName; }
    }


    private void clearForm() {
        reportTypeComboBox.getSelectionModel().clearSelection();
        formatComboBox.getSelectionModel().clearSelection();
        filterTextField.clear();
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        statusTextArea.clear();
    }

    private void showStatus(String msg, boolean error) {
        statusTextArea.appendText(msg + "\n");
        if (error) {
            statusTextArea.setStyle("-fx-text-fill: red;");
        } else {
            statusTextArea.setStyle("-fx-text-fill: green;");
        }
    }
}
