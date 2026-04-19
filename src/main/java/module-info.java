module com.boatticket {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires java.sql;
    requires itextpdf;
    requires org.xerial.sqlitejdbc;
    requires jbcrypt;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;

    opens com.boatticket to javafx.fxml;
    opens com.boatticket.controller to javafx.fxml;
    opens com.boatticket.model to javafx.base, javafx.fxml;

    exports com.boatticket;
    exports com.boatticket.controller;
    exports com.boatticket.model;
    exports com.boatticket.util;
    exports com.boatticket.db;
}
