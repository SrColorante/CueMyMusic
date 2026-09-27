package com.cuemymusic;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class LoginController {

    @FXML private VBox onlineLoginBox;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    @FXML
    private void handleLocalAccess() throws IOException {
        System.out.println("Starting in local mode...");
        App.setRoot("main");
        App.setFullscreen(true);
    }

    @FXML
    private void handleOnlineSync() {
        // Mock endpoint connection setup
        String endpoint = "CMM.cristianrenosto.party";
        statusLabel.setText("Connecting to " + endpoint + "...");
        
        // In the future this will authenticate. For now we just mock and proceed.
        System.out.println("Connecting to endpoint: " + endpoint);
        try {
            App.setRoot("main");
            App.setFullscreen(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
