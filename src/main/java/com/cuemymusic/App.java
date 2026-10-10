package com.cuemymusic;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point e gestore della navigazione fra schermate.
 *
 * <p>Le schermate sono caricate una sola volta e messe in cache: tornare dal
 * login al main (o riaprire la stessa vista) non forza un secondo parsing FXML,
 * e il controller conserva cosi' il proprio stato.</p>
 */
public class App extends Application {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    private static final String RES_ROOT = "/com/cuemymusic/";
    private static final String STYLESHEET_PATH = RES_ROOT + "css/styles.css";

    /** Cache delle schermate gia' istanziate: performance e riuso dei controller. */
    private static final Map<String, Parent> SCENE_CACHE = new ConcurrentHashMap<>();

    private static Scene scene;
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        installFont();

        scene = new Scene(loadFXML("login"));
        applyStylesheet(scene);

        stage.setScene(scene);
        stage.setTitle("CueMyMusic");
        stage.setMinWidth(820);
        stage.setMinHeight(560);
        installWindowIcon(stage);
        stage.sizeToScene();
        stage.show();

        // Un'eccezione non gestita lascerebbe l'app in uno stato incoerente:
        // la si intercetta per mostrare un errore comprensibile invece di sparire.
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            LOG.log(Level.SEVERE, "Errore non gestito", error);
            Platform.runLater(() -> showError("Errore inatteso",
                    "Si e' verificato un errore imprevisto: " + describe(error)));
        });
    }

    @Override
    public void stop() {
        // Rilascio esplicito delle risorse native di MediaPlayer: senza questo
        // i processi terminano lasciando descrittori audio e stream aperti.
        MainController.shutdownAll();
        SCENE_CACHE.clear();
        primaryStage = null;
        scene = null;
    }

    /**
     * Sostituisce la radice della scena attiva.
     *
     * <p>Deve essere chiamata dall'FX thread: e' un'operazione di scena JavaFX e
     * lanciarla da un worker produce {@code IllegalStateException}.</p>
     */
    public static void setRoot(String fxml) throws IOException {
        if (!Platform.isFxApplicationThread()) {
            // Difesa esplicita invece di un IllegalStateException incomprensibile.
            throw new IllegalStateException("setRoot() deve essere chiamata dall'FX thread");
        }
        if (scene == null) {
            throw new IllegalStateException("Scena non inizializzata: impossibile navigare");
        }
        scene.setRoot(loadFXML(fxml));
    }

    public static void setFullscreen(boolean fullscreen) {
        Stage stage = primaryStage;
        if (stage == null) {
            return;
        }
        if (fullscreen) {
            // In fullscreen la finestra deve poter essere ridimensionata al ritorno,
            // altrimenti si resta con una finestra 1x1 al momento dell'uscita.
            stage.setFullScreen(true);
        } else {
            stage.setFullScreen(false);
        }
    }

    /** Carica la radice FXML dalla cache, istanziandola solo al primo uso. */
    private static Parent loadFXML(String fxml) throws IOException {
        String name = Objects.requireNonNull(fxml, "Nome della schermata mancante");
        Parent cached = SCENE_CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        // Il nome proviene solo da costanti interne, ma la validazione difese
        // comunque da un eventuale uso futuro con input non controllato.
        if (!name.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IOException("Nome schermata non valido: " + name);
        }
        URL resource = App.class.getResource(RES_ROOT + "fxml/" + name + ".fxml");
        if (resource == null) {
            throw new IOException("Schermata non trovata: " + name + ".fxml");
        }
        FXMLLoader loader = new FXMLLoader(resource);
        loader.setControllerFactory(type -> {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Impossibile istanziare il controller " + type.getName(), e);
            }
        });
        Parent root = loader.load();
        SCENE_CACHE.put(name, root);
        return root;
    }

    /** Applica il tema condiviso a una scena, senza duplicare i fogli di stile. */
    static void applyStylesheet(Scene target) {
        URL css = App.class.getResource(STYLESHEET_PATH);
        if (css == null || target == null) {
            LOG.warning("Foglio di stile non trovato: " + STYLESHEET_PATH);
            return;
        }
        String external = css.toExternalForm();
        if (!target.getStylesheets().contains(external)) {
            target.getStylesheets().add(external);
        }
    }

    /**
     * Applica il tema a una finestra secondaria (dialog, popup).
     *
     * <p>Il parametro e' un {@link Parent} e non un {@link Window}: e'
     * {@code getStylesheets()} a vivere su {@link Parent}, ed e' li' che il
     * chiamante deve agganciare il tema (in {@code showError} e' il
     * {@code DialogPane}). Con {@code Window} il metodo non compilava.</p>
     */
    static void applyStylesheet(Parent target) {
        URL css = App.class.getResource(STYLESHEET_PATH);
        if (css == null || target == null) {
            LOG.warning("Foglio di stile non trovato: " + STYLESHEET_PATH);
            return;
        }
        String external = css.toExternalForm();
        if (!target.getStylesheets().contains(external)) {
            target.getStylesheets().add(external);
        }
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    /**
     * Carica il font del tema, se presente, con fallback silenzioso sui font di
     * sistema. Lo stream va chiuso: su jar impaccato consuma una entry dello ZIP.
     */
    private static void installFont() {
        String path = RES_ROOT + "fonts/Comfortaa-Regular.ttf";
        try (InputStream in = App.class.getResourceAsStream(path)) {
            if (in != null && Font.loadFont(in, 13) == null) {
                LOG.info("Font non caricato, si usa il fallback di sistema: " + path);
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.INFO, "Font non disponibile, si usa il fallback di sistema", e);
        }
    }

    /** Icona della finestra; assente in jar non impacchettati, non e' un errore. */
    static void installWindowIcon(Stage stage) {
        URL icon = App.class.getResource(RES_ROOT + "icons/app.png");
        if (icon != null) {
            stage.getIcons().add(new Image(icon.toExternalForm()));
        }
    }

    static void showError(String title, String message) {
        var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("CueMyMusic");
        alert.setHeaderText(title);
        alert.setContentText(message);
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }
        applyStylesheet(alert.getDialogPane());
        alert.getDialogPane().getStyleClass().add("root-pane");
        alert.showAndWait();
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
