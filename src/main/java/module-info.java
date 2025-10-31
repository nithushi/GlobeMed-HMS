module lk.jiat.ee.globemed {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires com.github.librepdf.openpdf;
    requires java.desktop;


    opens lk.jiat.ee.globemed to javafx.fxml;
    exports lk.jiat.ee.globemed;
}