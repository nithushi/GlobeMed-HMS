package lk.jiat.ee.globemed;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {

        Parent root = FXMLLoader.load(getClass().getResource("/lk/jiat/ee/globemed/login.fxml"));


        Scene scene = new Scene(root);
        primaryStage.setTitle("GlobeMed - Login");
        primaryStage.setScene(scene);

        primaryStage.setMaximized(true);
        primaryStage.setResizable(true);

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}