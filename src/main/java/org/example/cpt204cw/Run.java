package org.example.cpt204cw;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/*
 * JavaFX entry point. It loads the FXML layout and shows the route-planning
 * interface; route computation is delegated to the controller and backend
 * support classes.
 */
public class Run extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Run.class.getResource("route-planner.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 700);
        stage.setTitle("Route Planner");
        stage.setScene(scene);
        stage.show();
    }
}
