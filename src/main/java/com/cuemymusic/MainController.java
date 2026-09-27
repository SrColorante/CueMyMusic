package com.cuemymusic;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
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

    @FXML
    public void initialize() {
        // Mock data
        playlistsList.getItems().addAll("Workout Mix", "Chill Vibes", "Favorites");
        localFilesList.getItems().addAll("Folder: /home/devCri/Music", "Folder: /Downloads");
        recentSongsList.getItems().addAll("Migos ft. Drake - Walk It Talk It", "Travis Scott - SICKO MODE", "Future - Mask Off");
        
        queueList.getItems().addAll("Migos ft. Drake - Walk It Talk It", "Post Malone - rockstar", "Kendrick Lamar - HUMBLE.");
        
        // Highlight current song in queue
        queueList.getSelectionModel().select(0);
        
        // Set current song metadata
        currentSongLabel.setText("Walk It Talk It");
        currentArtistLabel.setText("Migos ft. Drake");
    }

    @FXML
    private void togglePlayPause() {
        isPlaying = !isPlaying;
        playPauseButton.setText(isPlaying ? "⏸" : "▶");
    }

    @FXML
    private void toggleFullscreen() {
        Stage stage = (Stage) fullscreenButton.getScene().getWindow();
        stage.setFullScreen(!stage.isFullScreen());
    }
}
