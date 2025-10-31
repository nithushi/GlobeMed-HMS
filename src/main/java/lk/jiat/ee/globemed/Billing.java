package lk.jiat.ee.globemed;

import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import lk.jiat.ee.globemed.model.MySQLConnection;

import java.awt.*;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.time.format.DateTimeFormatter;

public class Billing {

    @FXML private TextField billingPatientName;
    @FXML private TextField billingPatientId;
    @FXML private TextField billAmount;
    @FXML private ComboBox<String> paymentMethodCombo;
    @FXML private DatePicker billingDate;
    @FXML private ComboBox<String> paymentStatusCombo;
    @FXML private TextArea billingNotes;
    @FXML private ComboBox<String> insuranceProviderCombo;
    @FXML private ProgressBar claimProgress;

    @FXML private TableView<ClaimRow> claimsTable;
    @FXML private TableColumn<ClaimRow, String> colPatientName;
    @FXML private TableColumn<ClaimRow, Number> colAmount;
    @FXML private TableColumn<ClaimRow, String> colInsurer;
    @FXML private TableColumn<ClaimRow, String> colPolicyNo;
    @FXML private TableColumn<ClaimRow, String> colStatus;
    @FXML private TableColumn<ClaimRow, String> colSubmitted;


    private final Map<String, Integer> paymentMethodIds = new HashMap<>();
    private final Map<String, Integer> billingStatusIds  = new HashMap<>();
    private final Map<String, Integer> claimStatusIds    = new HashMap<>();
    @FXML
    private ObservableList<ClaimRow> claims = FXCollections.observableArrayList();

    //Bridge Pattern used for Bill Payment

    //Abstraction
    interface BillingService {
        int createBill(int patientId, String patientName, double amount,
                       LocalDate dateIssued, String notes, int billingStatusId, int paymentMethodId) throws Exception;

        void settlePayment(int billId, double amount) throws Exception;
    }

    //Implementor
    interface PaymentProcessor {
        void process(int billId, double amount) throws Exception;
    }

    //Refined Abstraction
    static class BillingServiceImpl implements BillingService {
        private final PaymentProcessor processor;
        public BillingServiceImpl(PaymentProcessor processor) { this.processor = processor; }

        @Override
        public int createBill(int patientId, String patientName, double amount,
                              LocalDate dateIssued, String notes, int billingStatusId, int paymentMethodId) throws Exception {
            String pName = escape(patientName);
            String n = notes == null ? "" : escape(notes);
            String q = "INSERT INTO billing (patient_id, patient_name, amount, date_issued, billing_status_id, payment_method_id) " +
                    "VALUES ("+patientId+", '"+pName+"', "+amount+", '"+dateIssued+"', "+billingStatusId+", "+paymentMethodId+")";
            MySQLConnection.execute(q);

            ResultSet rs = MySQLConnection.execute("SELECT LAST_INSERT_ID() AS id");
            int billId = 0;
            if (rs != null && rs.next()) billId = rs.getInt("id");
            return billId;
        }

        @Override
        public void settlePayment(int billId, double amount) throws Exception {
            processor.process(billId, amount);
        }
    }

    //Concrete Implementors
    static class CashProcessor implements PaymentProcessor {
        @Override public void process(int billId, double amount) {}
    }

    static class CardProcessor implements PaymentProcessor {
        @Override public void process(int billId, double amount) { /* could log card txn table here */ }
    }

    // Chain of Responsibility pattern used for Claim Approval
    static class InsuranceProcessor implements PaymentProcessor {
        private final String insurerName;
        public InsuranceProcessor(String insurerName) { this.insurerName = insurerName; }

        @Override
        public void process(int billId, double amount) throws Exception {
            // status "Submitted"
            int statusSubmitted = getStatusIdByName("claim_status", "status", "Submitted");
            String q = "INSERT INTO insurance_claim (bill_id, insurance_company, claim_status_id) " +
                    "VALUES ("+billId+", '"+escape(insurerName)+"', "+statusSubmitted+")";
            MySQLConnection.execute(q);

        }
    }

    //Chain of Responsibility (only for Insurance approvals)

    //Context passed through chain
    static class ClaimContext {
        final int claimId;
        ClaimContext(int claimId){ this.claimId = claimId; }
    }

    //Handler
    abstract static class ClaimHandler {
        private ClaimHandler next;
        public ClaimHandler linkWith(ClaimHandler next){ this.next = next; return next; }
        public final void handle(ClaimContext ctx) throws Exception {
            if (doHandle(ctx) && next != null) next.handle(ctx);
        }
        protected abstract boolean doHandle(ClaimContext ctx) throws Exception;
    }

    static class SubmitHandler extends ClaimHandler {
        @Override protected boolean doHandle(ClaimContext ctx) throws Exception {
            int st = getStatusIdByName("claim_status","status","Submitted");
            MySQLConnection.execute("UPDATE insurance_claim SET claim_status_id="+st+" WHERE claim_id="+ctx.claimId);
            return true;
        }
    }
    static class ManagerReviewHandler extends ClaimHandler {
        @Override protected boolean doHandle(ClaimContext ctx) throws Exception {
            int st = getStatusIdByName("claim_status","status","Manager Approved");
            MySQLConnection.execute("UPDATE insurance_claim SET claim_status_id="+st+" WHERE claim_id="+ctx.claimId);
            return true;
        }
    }
    static class InsurerCheckHandler extends ClaimHandler {
        @Override protected boolean doHandle(ClaimContext ctx) throws Exception {
            int st = getStatusIdByName("claim_status","status","Insurer Verified");
            MySQLConnection.execute("UPDATE insurance_claim SET claim_status_id="+st+" WHERE claim_id="+ctx.claimId);
            return true;
        }
    }
    static class FinalizeHandler extends ClaimHandler {
        @Override protected boolean doHandle(ClaimContext ctx) throws Exception {
            int st = getStatusIdByName("claim_status","status","Finalized");
            MySQLConnection.execute("UPDATE insurance_claim SET claim_status_id="+st+" WHERE claim_id="+ctx.claimId);
            return true;
        }
    }

    private static ClaimHandler buildDefaultChain(){
        return new SubmitHandler()
                .linkWith(new ManagerReviewHandler())
                .linkWith(new InsurerCheckHandler())
                .linkWith(new FinalizeHandler());
    }

    @FXML
    private void initialize() {

        loadPaymentMethods();
        loadBillingStatuses();
        loadClaimStatuses();

        billingDate.setValue(LocalDate.now());
        paymentStatusCombo.setConverter(new StringConverter<>() {
            @Override public String toString(String s) { return s; }
            @Override public String fromString(String s) { return s; }
        });

        // fill claims table
        buildClaimsTableColumns();
        claimsTable.setItems(claims);
        refreshClaimsTable();
    }

    @FXML
    private void onGenerateBill() {
        try {
            int patientId = parseInt(billingPatientId.getText());
            String pName = must(billingPatientName.getText(), "Patient Name required");
            double amount = parseDouble(billAmount.getText());
            LocalDate date = billingDate.getValue() == null ? LocalDate.now() : billingDate.getValue();
            String pmName = must(paymentMethodCombo.getValue(), "Select a payment method");
            String bsName = paymentStatusCombo.getValue() == null ? "Pending" : paymentStatusCombo.getValue();
            String notes = billingNotes.getText();

            int pmId = idOrThrow(paymentMethodIds, pmName, "Unknown payment method");
            int bsId = idOrFallback(billingStatusIds, bsName, "Pending");

            PaymentProcessor processor = switch (pmName) {
                case "Cash" -> new CashProcessor();
                case "Credit Card", "Debit Card" -> new CardProcessor();
                case "Insurance" -> new InsuranceProcessor(
                        insuranceProviderCombo.getValue() == null ? "Unknown" : insuranceProviderCombo.getValue());
                default -> new CashProcessor();
            };

            BillingService svc = new BillingServiceImpl(processor);

            int billId = svc.createBill(patientId, pName, amount, date, notes, bsId, pmId);
            svc.settlePayment(billId, amount);

            generateBillReport(billId, patientId, pName, amount, pmName, bsName, date, notes);

            ok("Bill #" + billId + " created.");
            clearBillingForm();
            refreshClaimsTable();
        } catch (Exception ex) {
            error("Generate bill failed: " + ex.getMessage());
        }
    }


    private void generateBillReport(int billId, int patientId, String patientName,
                                    double amount, String paymentMethod, String status,
                                    LocalDate date, String notes) {
        try {
            String fileName = "GlobeMed_Bill_" + billId + "_" +
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf";
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter.getInstance(document, new FileOutputStream(fileName));
            document.open();

            // Header with company logo area and styling
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{1, 2});

            // Logo placeholder cell
            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setPadding(10);
            logoCell.setBackgroundColor(new Color(41, 128, 185)); // Professional blue
            Paragraph logoText = new Paragraph("GM",
                    new Font(Font.HELVETICA, 24, Font.BOLD, Color.WHITE));
            logoText.setAlignment(Element.ALIGN_CENTER);
            logoCell.addElement(logoText);
            headerTable.addCell(logoCell);

            // Company info cell
            PdfPCell companyCell = new PdfPCell();
            companyCell.setBorder(Rectangle.NO_BORDER);
            companyCell.setPadding(10);
            companyCell.setBackgroundColor(new Color(52, 152, 219)); // Lighter blue

            Font companyFont = new Font(Font.HELVETICA, 18, Font.BOLD, Color.WHITE);
            Font addressFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.WHITE);

            Paragraph companyName = new Paragraph("GlobeMed Healthcare", companyFont);
            companyName.setSpacingAfter(5);
            companyCell.addElement(companyName);

            companyCell.addElement(new Paragraph("123 Medical Center Drive, Colombo 07", addressFont));
            companyCell.addElement(new Paragraph("+94 11 234 5678 | billing@globemed.lk", addressFont));
            companyCell.addElement(new Paragraph("www.globemed.lk", addressFont));

            headerTable.addCell(companyCell);
            document.add(headerTable);

            document.add(new Paragraph(" ", new Font(Font.HELVETICA, 8)));

            // Title with stylish formatting
            Font titleFont = new Font(Font.HELVETICA, 22, Font.BOLD, new Color(41, 128, 185));
            Paragraph title = new Paragraph(" BILLING STATEMENT", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            document.add(title);

            // Bill number and date info bar
            PdfPTable infoBar = new PdfPTable(2);
            infoBar.setWidthPercentage(100);
            infoBar.setSpacingAfter(15);

            PdfPCell billNoCell = new PdfPCell();
            billNoCell.setBackgroundColor(new Color(236, 240, 241));
            billNoCell.setPadding(8);
            billNoCell.setBorder(Rectangle.NO_BORDER);
            billNoCell.addElement(new Paragraph("Bill No: #" + String.format("%06d", billId),
                    new Font(Font.HELVETICA, 12, Font.BOLD)));
            infoBar.addCell(billNoCell);

            PdfPCell dateCell = new PdfPCell();
            dateCell.setBackgroundColor(new Color(236, 240, 241));
            dateCell.setPadding(8);
            dateCell.setBorder(Rectangle.NO_BORDER);
            dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            dateCell.addElement(new Paragraph("Generated: " +
                    LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    new Font(Font.HELVETICA, 12, Font.BOLD)));
            infoBar.addCell(dateCell);

            document.add(infoBar);

            // Patient Information Section
            Font sectionFont = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(52, 73, 94));
            Paragraph patientHeader = new Paragraph("👤 PATIENT INFORMATION", sectionFont);
            patientHeader.setSpacingBefore(5);
            patientHeader.setSpacingAfter(8);
            document.add(patientHeader);

            PdfPTable patientTable = new PdfPTable(2);
            patientTable.setWidthPercentage(100);
            patientTable.setWidths(new float[]{1, 2});
            patientTable.setSpacingAfter(15);

            // Style patient table
            addStyledTableRow(patientTable, "Patient ID", String.valueOf(patientId), true);
            addStyledTableRow(patientTable, "Patient Name", patientName, false);

            document.add(patientTable);

            // Billing Details Section
            Paragraph billHeader = new Paragraph("BILLING DETAILS", sectionFont);
            billHeader.setSpacingAfter(8);
            document.add(billHeader);

            PdfPTable billTable = new PdfPTable(2);
            billTable.setWidthPercentage(100);
            billTable.setWidths(new float[]{1, 2});
            billTable.setSpacingAfter(15);

            addStyledTableRow(billTable, "Service Date",
                    date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), true);
            addStyledTableRow(billTable, "Payment Method", paymentMethod, false);

            // Status with color coding
            Color statusColor = status.equalsIgnoreCase("Paid") ?
                    new Color(46, 204, 113) : new Color(231, 76, 60);
            addStyledTableRow(billTable, "Status", status, false, statusColor);

            if (notes != null && !notes.isBlank()) {
                addStyledTableRow(billTable, "Notes", notes, true);
            }

            document.add(billTable);

            //Amount Section
            PdfPTable amountTable = new PdfPTable(1);
            amountTable.setWidthPercentage(100);
            amountTable.setSpacingAfter(20);

            PdfPCell amountCell = new PdfPCell();
            amountCell.setBackgroundColor(new Color(52, 152, 219));
            amountCell.setPadding(15);
            amountCell.setBorder(Rectangle.NO_BORDER);

            Font amountFont = new Font(Font.HELVETICA, 20, Font.BOLD, Color.WHITE);
            Paragraph amountText = new Paragraph("TOTAL AMOUNT: LKR " + String.format("%.2f", amount), amountFont);
            amountText.setAlignment(Element.ALIGN_CENTER);
            amountCell.addElement(amountText);
            amountTable.addCell(amountCell);

            document.add(amountTable);

            // Footer section
            PdfPTable footerTable = new PdfPTable(1);
            footerTable.setWidthPercentage(100);
            footerTable.setSpacingBefore(30);

            PdfPCell footerCell = new PdfPCell();
            footerCell.setBorder(Rectangle.TOP);
            footerCell.setBorderColor(new Color(189, 195, 199));
            footerCell.setPadding(15);
            footerCell.setBackgroundColor(new Color(248, 249, 250));

            Font footerFont = new Font(Font.HELVETICA, 10, Font.ITALIC, new Color(127, 140, 141));
            footerCell.addElement(new Paragraph("Thank you for choosing GlobeMed Healthcare!", footerFont));
            footerCell.addElement(new Paragraph("For inquiries, please contact our billing department at +94 11 234 5678", footerFont));
            footerCell.addElement(new Paragraph(" ", footerFont));

            Paragraph signature = new Paragraph("Authorized Signature: ___________________________",
                    new Font(Font.HELVETICA, 12, Font.BOLD));
            signature.setAlignment(Element.ALIGN_RIGHT);
            footerCell.addElement(signature);

            footerTable.addCell(footerCell);
            document.add(footerTable);

            document.close();
            System.out.println("Bill receipt generated successfully: " + fileName);

            // Auto-open the PDF
            File pdfFile = new File(fileName);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(pdfFile);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("PDF generation failed: " + e.getMessage());
        }
    }

    private void addStyledTableRow(PdfPTable table, String label, String value,
                                   boolean isAlternate) {
        addStyledTableRow(table, label, value, isAlternate, null);
    }

    private void addStyledTableRow(PdfPTable table, String label, String value,
                                   boolean isAlternate, Color valueColor) {
        // Label cell
        PdfPCell labelCell = new PdfPCell();
        labelCell.setBackgroundColor(isAlternate ? new Color(236, 240, 241) : Color.WHITE);
        labelCell.setPadding(8);
        labelCell.setBorder(Rectangle.BOTTOM);
        labelCell.setBorderColor(new Color(189, 195, 199));
        labelCell.addElement(new Paragraph(label,
                new Font(Font.HELVETICA, 11, Font.BOLD, new Color(52, 73, 94))));
        table.addCell(labelCell);

        // Value cell
        PdfPCell valueCell = new PdfPCell();
        valueCell.setBackgroundColor(isAlternate ? new Color(236, 240, 241) : Color.WHITE);
        valueCell.setPadding(8);
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorderColor(new Color(189, 195, 199));

        Color textColor = valueColor != null ? valueColor : new Color(44, 62, 80);
        valueCell.addElement(new Paragraph(value,
                new Font(Font.HELVETICA, 11, Font.NORMAL, textColor)));
        table.addCell(valueCell);
    }

    @FXML
    private void onClearBilling() {
        clearBillingForm();
    }

    //Claim workflow buttons
    @FXML
    private void onSubmitClaim() {
        advanceSelectedClaimTo(new SubmitHandler());
    }

    @FXML
    private void onManagerReview() {
        advanceSelectedClaimTo(new ManagerReviewHandler());
    }

    @FXML
    private void onInsuranceCheck() {
        advanceSelectedClaimTo(new InsurerCheckHandler());
    }

    @FXML
    private void onFinalizePayment() {
        advanceSelectedClaimTo(new FinalizeHandler());
    }

    @FXML
    private void onRefreshClaims() {
        refreshClaimsTable();
    }

    //Helpers

    private void advanceSelectedClaimTo(ClaimHandler handler){
        try {
            ClaimRow row = claimsTable.getSelectionModel().getSelectedItem();
            if (row == null) { info("Select a claim row first."); return; }
            handler.handle(new ClaimContext(row.claimId.get()));
            refreshClaimsTable();
        } catch (Exception e) {
            error("Update failed: " + e.getMessage());
        }
    }

    private void clearBillingForm(){
        billingPatientId.clear();
        billingPatientName.clear();
        billAmount.clear();
        paymentMethodCombo.getSelectionModel().clearSelection();
        paymentStatusCombo.getSelectionModel().clearSelection();
        billingDate.setValue(LocalDate.now());
        billingNotes.clear();
    }

    private void loadPaymentMethods() {
        paymentMethodCombo.getItems().clear();
        paymentMethodIds.clear();
        try {
            ResultSet rs = MySQLConnection.execute("SELECT payment_method_id, payment_method FROM payment_method ORDER BY 1");
            while (rs != null && rs.next()) {
                String name = rs.getString("payment_method");
                int id = rs.getInt("payment_method_id");
                paymentMethodIds.put(name, id);
                if (!paymentMethodCombo.getItems().contains(name)) paymentMethodCombo.getItems().add(name);
            }
        } catch (Exception e) {
        }
    }

    private void loadBillingStatuses() {
        paymentStatusCombo.getItems().clear();
        billingStatusIds.clear();
        try {
            ResultSet rs = MySQLConnection.execute("SELECT status_id, status FROM billing_status ORDER BY 1");
            while (rs != null && rs.next()) {
                String name = rs.getString("status");
                int id = rs.getInt("status_id");
                billingStatusIds.put(name, id);
                paymentStatusCombo.getItems().add(name);
            }
        } catch (Exception ignored) {
            paymentStatusCombo.getItems().addAll("Pending","Paid","Cancelled");
        }
    }

    private void loadClaimStatuses() {
        claimStatusIds.clear();
        try {
            ResultSet rs = MySQLConnection.execute("SELECT status_id, status FROM claim_status ORDER BY 1");
            while (rs != null && rs.next()) {
                claimStatusIds.put(rs.getString("status"), rs.getInt("status_id"));
            }
        } catch (Exception ignored) {}
    }

    private void buildClaimsTableColumns() {
        colPatientName.setCellValueFactory(d -> d.getValue().patientName);
        colAmount.setCellValueFactory(d -> d.getValue().amount);
        colInsurer.setCellValueFactory(d -> d.getValue().insurer);
        colPolicyNo.setCellValueFactory(d -> d.getValue().policyNo);
        colStatus.setCellValueFactory(d -> d.getValue().status);
        colSubmitted.setCellValueFactory(d -> d.getValue().submitted);
    }

    private void refreshClaimsTable() {
        ObservableList<ClaimRow> rows = FXCollections.observableArrayList();
        try {
            String q = """
                SELECT ic.claim_id, b.patient_name, b.amount, ic.insurance_company,
                       IFNULL(p.policy_number,'') AS policy_no,
                       cs.status, b.date_issued
                FROM insurance_claim ic
                JOIN billing b ON b.bill_id = ic.bill_id
                LEFT JOIN claim_status cs ON cs.status_id = ic.claim_status_id
                LEFT JOIN (
                    SELECT bill_id, MAX(policy_number) AS policy_number
                    FROM insurance_claim GROUP BY bill_id
                ) p ON p.bill_id = b.bill_id
                ORDER BY ic.claim_id DESC
                """;
            ResultSet rs = MySQLConnection.execute(q);
            while (rs != null && rs.next()){
                ClaimRow r = new ClaimRow(
                        rs.getInt("claim_id"),
                        rs.getString("patient_name"),
                        rs.getDouble("amount"),
                        rs.getString("insurance_company"),
                        rs.getString("policy_no"),
                        rs.getString("status"),
                        String.valueOf(rs.getTimestamp("date_issued").toLocalDateTime())
                );
                rows.add(r);
            }
        } catch (Exception e) {

        }
        claimsTable.setItems(rows);
        // visual progress bar: map table selection to progress
        claimsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, cur) -> {
            if (cur == null) { claimProgress.setProgress(0); return; }
            claimProgress.setProgress(progressForStatus(cur.status.get()));
        });
    }

    private double progressForStatus(String status){
        if (status == null) return 0;
        return switch (status) {
            case "Submitted" -> 0.25;
            case "Manager Approved" -> 0.50;
            case "Insurer Verified" -> 0.75;
            case "Finalized" -> 1.00;
            default -> 0.10;
        };
    }

    public static class ClaimRow {
        final SimpleIntegerProperty claimId = new SimpleIntegerProperty();
        final SimpleStringProperty patientName = new SimpleStringProperty();
        final SimpleDoubleProperty amount = new SimpleDoubleProperty();
        final SimpleStringProperty insurer = new SimpleStringProperty();
        final SimpleStringProperty policyNo = new SimpleStringProperty();
        final SimpleStringProperty status = new SimpleStringProperty();
        final SimpleStringProperty submitted = new SimpleStringProperty();
        ClaimRow(int id, String p, double a, String ins, String pol, String st, String sub){
            claimId.set(id); patientName.set(p); amount.set(a);
            insurer.set(ins); policyNo.set(pol); status.set(st); submitted.set(sub);
        }

    }

    private static String must(String v, String msg){
        if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(msg);
        return v.trim();
    }
    private static int parseInt(String s){ return Integer.parseInt(must(s,"Patient ID required")); }
    private static double parseDouble(String s){ return Double.parseDouble(must(s,"Amount required")); }
    private static String escape(String s){ return s == null ? "" : s.replace("'", "''"); }

    private static int idOrThrow(Map<String,Integer> map, String key, String msg){
        Integer v = map.get(key);
        if (v == null) throw new IllegalArgumentException(msg + " ("+key+")");
        return v;
    }
    private static int idOrFallback(Map<String,Integer> map, String key, String fallbackKey){
        Integer v = map.get(key);
        if (v != null) return v;
        return map.getOrDefault(fallbackKey, 1);
    }

    private static int getStatusIdByName(String table, String col, String name) throws Exception {
        ResultSet rs = MySQLConnection.execute(
                "SELECT status_id FROM "+table+" WHERE "+col+" = '"+escape(name)+"' LIMIT 1");
        if (rs != null && rs.next()) return rs.getInt(1);
        // if not exists, create it quickly
        MySQLConnection.execute("INSERT INTO "+table+" ("+col+") VALUES ('"+escape(name)+"')");
        rs = MySQLConnection.execute("SELECT LAST_INSERT_ID()");
        rs.next(); return rs.getInt(1);
    }

    private void ok(String m){ new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait(); }
    private void info(String m){ new Alert(Alert.AlertType.NONE, m, ButtonType.OK).showAndWait(); }
    private void error(String m){ new Alert(Alert.AlertType.ERROR, m, ButtonType.OK).showAndWait(); }
}
