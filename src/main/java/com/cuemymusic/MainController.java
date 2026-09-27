package com.cuemymusic;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;

public class MainController {

    @FXML private ListView<String> localFilesList;
    @FXML private ListView<String> playlistsList;
    @FXML private ListView<String> recentSongsList;
    @FXML private ListView<String> queueList;
    
    @FXML private Label currentSongLabel;
    @FXML private Label currentArtistLabel;
    @FXML private Slider progressBar;
    @FXML private Button playPauseButton;
    @FXML private Button fullscreenButton;
    
    private boolean isPlaying = false;
    private boolean hasMedia = false; // flag to track if a song is loaded

    @FXML
    public void initialize() {
        // Nessun dato fantoccio inserito
        
        // Impostiamo testo di base per la barra
        currentSongLabel.setText("Nessun brano in riproduzione");
        currentArtistLabel.setText("-");
    }

    @FXML
    private void togglePlayPause() {
        if (!hasMedia) {
            showError("Nessun file multimediale selezionato", "Seleziona prima un brano dalla libreria per avviare la riproduzione.");
            return;
        }
        
        isPlaying = !isPlaying;
        playPauseButton.setText(isPlaying ? "⏸" : "▶");
    }

    @FXML
    private void toggleFullscreen() {
        Stage stage = (Stage) fullscreenButton.getScene().getWindow();
        stage.setFullScreen(!stage.isFullScreen());
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Errore");
        alert.setHeaderText(title);
        alert.setContentText(message);
        
        // Applica lo stile
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/com/cuemymusic/css/styles.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("root-pane");
        
        alert.showAndWait();
    }
}
