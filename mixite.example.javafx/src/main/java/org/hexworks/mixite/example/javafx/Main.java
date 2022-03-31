package org.hexworks.mixite.example.javafx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main extends Application
{

    public static void main(String[] args)
    {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage)
    {
        // Set up the main scene. This is the controller, so it's critical.
        Parent root;
        try
        {
            System.out.println("Starting application.");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/MixiteExample.fxml"));
            root = loader.load();
            primaryStage.setTitle("Mixite JavaFX Example");
            final Scene streamerInterfaceScene = new Scene(root, 800, 620);
            primaryStage.setScene(streamerInterfaceScene);
            primaryStage.show();
            primaryStage.setOnCloseRequest(windowEvent ->
            {
                Platform.exit();
                System.exit(0);
            });
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
