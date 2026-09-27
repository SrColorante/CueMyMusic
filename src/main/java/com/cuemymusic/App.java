package com.cuemymusic;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

public class App extends Application {

    private static Scene scene;
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        // Load custom fonts (fallback to system fonts)
        // Font.loadFont(getClass().getResourceAsStream("/com/cuemymusic/fonts/Comfortaa-Regular.ttf"), 14);
        // Font.loadFont(getClass().getResourceAsStream("/com/cuemymusic/fonts/Comfortaa-Bold.ttf"), 14);
        
        scene = new Scene(loadFXML("login"));
        scene.getStylesheets().add(getClass().getResource("/com/cuemymusic/css/styles.css").toExternalForm());
        
        stage.setScene(scene);
        stage.setTitle("CueMyMusic");
        stage.sizeToScene(); // Auto-size to content
        stage.show();
    }

    public static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }
    
    public static void setFullscreen(boolean fullscreen) {
        primaryStage.setFullScreen(fullscreen);
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("/com/cuemymusic/fxml/" + fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }
}
