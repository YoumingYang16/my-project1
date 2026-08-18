/*
 * Java module declaration for the JavaFX route-planning application.
 * The javafx.fxml opening is required so FXML can access the controller.
 */
module org.example.cpt204cw {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;

    opens org.example.cpt204cw to javafx.fxml;
    exports org.example.cpt204cw;
}
