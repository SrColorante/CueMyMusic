# Spiegazione del Codice: CueMyMusic

Come richiesto, ecco la spiegazione dettagliata riga per riga (o a blocchi logici) del codice scritto fino ad ora.

## `App.java`
Questo è il file principale che avvia l'interfaccia JavaFX.

```java
package com.cuemymusic; // Dichiara il pacchetto principale dell'applicazione

import javafx.application.Application; // Importa la classe base per le app JavaFX
import javafx.fxml.FXMLLoader; // Importa il caricatore di file FXML (per la UI)
import javafx.scene.Parent; // Classe base per tutti i nodi visivi
import javafx.scene.Scene; // Contenitore di tutti i contenuti di una finestra
import javafx.scene.text.Font; // Import per la gestione dei font
import javafx.stage.Stage; // Finestra principale dell'applicazione
import javafx.stage.StageStyle; // Stile della finestra (bordi, ecc.)

import java.io.IOException; // Gestione delle eccezioni di input/output

public class App extends Application { // La nostra classe principale estende Application di JavaFX

    private static Scene scene; // Variabile globale per la scena corrente (l'interno della finestra)
    private static Stage primaryStage; // Variabile globale per la finestra stessa

    @Override
    public void start(Stage stage) throws IOException { // Metodo chiamato all'avvio dell'app
        primaryStage = stage; // Salviamo il riferimento alla finestra per poterla modificare dopo
        
        // I caricamenti custom del font sono stati commentati per far usare al sistema il Comfortaa nativo
        // Font.loadFont(getClass().getResourceAsStream("/com/cuemymusic/fonts/Comfortaa-Regular.ttf"), 14);
        
        // Carichiamo l'interfaccia di login da login.fxml, impostando 800x600 come risoluzione iniziale
        scene = new Scene(loadFXML("login"), 800, 600);
        
        // Aggiungiamo il nostro file CSS globale (styles.css) per stilizzare la schermata
        scene.getStylesheets().add(getClass().getResource("/com/cuemymusic/css/styles.css").toExternalForm());
        
        stage.setScene(scene); // Applichiamo la scena alla finestra
        stage.setTitle("CueMyMusic"); // Diamo un titolo alla finestra
        stage.show(); // Mostriamo a schermo la finestra
    }

    // Metodo helper per cambiare schermata facilmente (es: da login a main)
    public static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml)); // Sostituisce la root della scena con il nuovo file fxml
    }
    
    // Metodo per mettere la finestra a schermo intero
    public static void setFullscreen(boolean fullscreen) {
        primaryStage.setFullScreen(fullscreen); // Attiva o disattiva il fullscreen nativo
    }

    // Metodo helper che si occupa di caricare il file .fxml dal disco
    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("/com/cuemymusic/fxml/" + fxml + ".fxml"));
        return fxmlLoader.load();
    }

    // Metodo main standard di Java che lancia l'app JavaFX
    public static void main(String[] args) {
        launch();
    }
}
```

## `LoginController.java`
Questo controller gestisce la logica dei bottoni della pagina di login.

```java
package com.cuemymusic;

import javafx.fxml.FXML; // Annotazione per collegare variabili Java a elementi del FXML
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import java.io.IOException;

public class LoginController {

    // Queste variabili sono iniettate automaticamente dal file login.fxml
    @FXML private VBox onlineLoginBox;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    // Metodo eseguito quando si preme "Accesso Locale"
    @FXML
    private void handleLocalAccess() throws IOException {
        System.out.println("Starting in local mode..."); // Log di debug
        App.setRoot("main"); // Cambia la vista caricando main.fxml
        App.setFullscreen(true); // Mette in automatico lo schermo intero, come da richiesta
    }

    // Metodo eseguito quando si preme "Accedi" nella sincronizzazione online
    @FXML
    private void handleOnlineSync() {
        String endpoint = "CMM.cristianrenosto.party"; // L'endpoint richiesto per il sync
        statusLabel.setText("Connecting to " + endpoint + "..."); // Mostra il testo in UI
        
        System.out.println("Connecting to endpoint: " + endpoint);
        try {
            App.setRoot("main"); // Per ora va avanti lo stesso (mocking del login)
            App.setFullscreen(true); // Fullscreen
        } catch (IOException e) {
            e.printStackTrace(); // In caso di errore, stampa lo stack trace
        }
    }
}
```

## `MainController.java`
Questo gestisce l'interfaccia principale e il player mockato di "Walk It Talk It".

```java
package com.cuemymusic;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class MainController {

    // Dichiarazione di tutte le liste visibili nell'interfaccia
    @FXML private ListView<String> localFilesList;
    @FXML private ListView<String> playlistsList;
    @FXML private ListView<String> recentSongsList;
    @FXML private ListView<String> queueList;
    
    // Dichiarazione degli elementi della Waybar inferiore
    @FXML private Label currentSongLabel;
    @FXML private Label currentArtistLabel;
    @FXML private Slider progressBar;
    @FXML private Button playPauseButton;
    @FXML private Button fullscreenButton;
    
    private boolean isPlaying = false; // Stato del pulsante Play/Pause

    // Metodo chiamato in automatico da JavaFX quando la view è stata caricata
    @FXML
    public void initialize() {
        // Popolamento liste con dati finti di test
        playlistsList.getItems().addAll("Workout Mix", "Chill Vibes", "Favorites");
        localFilesList.getItems().addAll("Folder: /home/devCri/Music", "Folder: /Downloads");
        recentSongsList.getItems().addAll("Migos ft. Drake - Walk It Talk It", "Travis Scott - SICKO MODE", "Future - Mask Off");
        
        // Popolamento della coda
        queueList.getItems().addAll("Migos ft. Drake - Walk It Talk It", "Post Malone - rockstar", "Kendrick Lamar - HUMBLE.");
        
        // Selezioniamo il primo elemento (Walk It Talk It) affinché il CSS lo colori di bianco e lo evidenzi
        queueList.getSelectionModel().select(0);
        
        // Impostiamo titolo e artista nella Waybar
        currentSongLabel.setText("Walk It Talk It");
        currentArtistLabel.setText("Migos ft. Drake");
    }

    // Metodo per gestire il tasto Play/Pausa
    @FXML
    private void togglePlayPause() {
        isPlaying = !isPlaying; // Inverte lo stato booleano
        playPauseButton.setText(isPlaying ? "⏸" : "▶"); // Cambia l'icona del pulsante
    }

    // Metodo per uscire/entrare in fullscreen premendo il pulsante sulla destra della waybar
    @FXML
    private void toggleFullscreen() {
        Stage stage = (Stage) fullscreenButton.getScene().getWindow(); // Recupera la finestra
        stage.setFullScreen(!stage.isFullScreen()); // Inverte lo stato attuale
    }
}
```
