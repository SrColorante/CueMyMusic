package com.cuemymusic;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller della schermata principale: libreria locale, coda e barra di
 * riproduzione.
 *
 * <p>Vincoli rispettati da questo controller:</p>
 * <ul>
 *   <li><b>FX thread</b> — ogni accesso a Media, MediaPlayer e alla scena avviene
 *       dall'Application Thread. Le operazioni pesanti (scansione del filesystem,
 *       apertura dei file) stanno su {@link Service} dedicati.</li>
 *   <li><b>Niente I/O bloccante sul FX thread</b> — la costruzione di
 *       {@link Media} legge l'header del file e va eseguita in background.</li>
 *   <li><b>Leak</b> — {@link MediaPlayer} e {@link Media} sono rilasciati
 *       esplicitamente e i listener sono scollegati prima di sostituirli.</li>
 * </ul>
 */
public class MainController {

    private static final Logger LOG = Logger.getLogger(MainController.class.getName());

    /** Istanze vive, per un rilascio pulito alla chiusura dell'applicazione. */
    private static final List<MainController> INSTANCES = new CopyOnWriteArrayList<>();

    private static final int RECENT_LIMIT = 30;
    private static final double VOLUME_STEP = 0.05;
    private static final Duration POLL_INTERVAL = Duration.millis(100);

    // --- Libreria e liste -------------------------------------------------
    @FXML private ListView<String> playlistsList;
    @FXML private ListView<Track> localFilesList;
    @FXML private ListView<Track> recentSongsList;
    @FXML private ListView<Track> queueList;

    // --- Barra di riproduzione -------------------------------------------
    @FXML private Label currentSongLabel;
    @FXML private Label currentArtistLabel;
    @FXML private Label currentDetailLabel;
    @FXML private Label elapsedLabel;
    @FXML private Label durationLabel;
    @FXML private Slider progressBar;
    @FXML private Slider volumeSlider;
    @FXML private Button playPauseButton;
    @FXML private Button previousButton;
    @FXML private Button nextButton;
    @FXML private Button fullscreenButton;
    @FXML private Button muteButton;

    // --- Stato e feedback -------------------------------------------------
    @FXML private Label statusLabel;
    @FXML private Label libraryCountLabel;
    @FXML private Label queueCountLabel;
    @FXML private ProgressBar loadingIndicator;
    @FXML private TextField searchField;
    @FXML private VBox emptyStateBox;
    @FXML private Node rootPane;
    @FXML private Button addToQueueButton;

    private final ObservableList<Track> library = FXCollections.observableArrayList();
    private final ObservableList<Track> recent = FXCollections.observableArrayList();
    private final ObservableList<Track> queue = FXCollections.observableArrayList();
    private final Deque<Track> playbackHistory = new ArrayDeque<>();

    private MediaPlayer player;
    private Media media;
    private Track currentTrack;

    private LibraryService libraryService;
    private MediaLoadTask mediaLoadTask;
    private Timeline pollTimer;
    private PauseTransition searchDebounce;

    /** Vero mentre l'utente trascina la barra: in quel caso il polling non la tocca. */
    private boolean scrubbing;
    /** Vero mentre il frame di aggiornamento sta scrivendo il valore. */
    private boolean syncingFromPlayer;
    private boolean pendingSeek;
    private boolean shortcutsInstalled;
    private double volumeBeforeMute = 0.8;

    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    @FXML
    public void initialize() {
        INSTANCES.add(this);
        configureLabels();
        configureLists();
        configureTransport();
        configureSearch();
        configureFeedback();
        bindShortcutsWhenSceneReady();
    }

    // =====================================================================
    // Configurazione della UI
    // =====================================================================

    private void configureLabels() {
        currentSongLabel.setText("Nessun brano in riproduzione");
        currentArtistLabel.setText("\u2014");
        currentDetailLabel.setText("");
        currentArtistLabel.getStyleClass().add("muted-label");
        currentDetailLabel.getStyleClass().add("muted-label");
    }

    private void configureLists() {
        localFilesList.setItems(library);
        recentSongsList.setItems(recent);
        queueList.setItems(queue);

        setTrackCellFactory(localFilesList, false);
        setTrackCellFactory(recentSongsList, false);
        setTrackCellFactory(queueList, true);
        playlistsList.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setGraphic(null);
            }
        });

        // Doppio click = riproduci, su tutte le liste di brani.
        for (ListView<Track> list : List.of(localFilesList, recentSongsList, queueList)) {
            list.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    Track selected = list.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        playTrack(selected);
                    }
                }
            });
            list.setAccessibleRole(AccessibleRole.LIST);
        }

        queueCountLabel.textProperty().bind(Bindings.size(queue).asString());
        libraryCountLabel.textProperty().bind(Bindings.size(library)
                .asString("Brani in libreria: %d"));

        addToQueueButton.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> localFilesList.getSelectionModel().getSelectedItem() == null,
                        localFilesList.selectionModel().selectedItemProperty()));

        // Il riquadro "libreria vuota" appare solo quando non c'e' davvero nulla
        // da mostrare, filtraggio incluso.
        emptyStateBox.visibleProperty().bind(
                Bindings.createBooleanBinding(
                        () -> library.isEmpty(), localFilesList.itemsProperty()));
    }

    /**
     * Cella a due righe con nome artista e dettaglio, piu' testo accessibile
     * per i screen reader che altrimenti leggerebbero solo la riga vuota.
     */
    private void setTrackCellFactory(ListView<Track> list, boolean showRemoveHint) {
        list.setCellFactory(view -> new ListCell<>() {
            private final Label title = new Label();
            private final Label subtitle = new Label();
            private final VBox box = new VBox(title, subtitle);

            {
                box.setSpacing(2);
                box.setAlignment(Pos.CENTER_LEFT);
                box.setPadding(new Insets(4, 10, 4, 10));
                box.getStyleClass().add("track-cell");
                title.getStyleClass().add("track-title");
                subtitle.getStyleClass().add("track-subtitle");
                title.setMaxWidth(Double.MAX_VALUE);
                subtitle.setMaxWidth(Double.MAX_VALUE);
            }

            @Override
            protected void updateItem(Track item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setAccessibleText(null);
                    return;
                }
                title.setText(item.title());
                String sub = item.subtitle();
                subtitle.setText(showRemoveHint && sub.isEmpty() ? "Doppio click per riprodurre" : sub);
                subtitle.setVisible(!subtitle.getText().isEmpty());
                setGraphic(box);
                setText(null);
                setAccessibleText(item.displayLabel());
            }
        });
    }

    private void configureTransport() {
        progressBar.setMax(1);
        progressBar.setValue(0);
        progressBar.setDisable(true);
        progressBar.setTooltip(new Tooltip("Trascina per spostarti nel brano"));
        progressBar.setAccessibleText("Posizione nel brano");

        // valueChanging() e' true per tutta la durata del trascinamento: e' il
        // segnale giusto per sospendere il polling, che altrimenti continuerebbe
        // a scrivere il valore e a far "saltare" il pollice sotto il cursore.
        progressBar.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (Boolean.TRUE.equals(isChanging)) {
                scrubbing = true;
            } else if (scrubbing) {
                scrubbing = false;
                pendingSeek = true;
                double target = progressBar.getValue();
                elapsedLabel.setText(MediaFiles.formatDuration(target));
            }
        });
        // Copre anche la modifica da tastiera (frecce/pagina), dove valueChanging
        // resta false e senza questo listener la barra non si muoverebbe.
        progressBar.valueProperty().addListener((obs, old, value) -> {
            if (syncingFromPlayer) {
                return;
            }
            pendingSeek = true;
            elapsedLabel.setText(MediaFiles.formatDuration(value.doubleValue()));
        });

        elapsedLabel.setText("0:00");
        durationLabel.setText("--:--");

        volumeSlider.setValue(0.8);
        volumeSlider.setMax(1);
        volumeSlider.setBlockIncrement(VOLUME_STEP);
        volumeSlider.setTooltip(new Tooltip("Volume (freccia su/giù)"));
        volumeSlider.setAccessibleText("Volume");
        volumeSlider.valueProperty().addListener((obs, old, value) -> {
            if (player != null) {
                player.setVolume(value.doubleValue());
            }
            if (value.doubleValue() > 0) {
                volumeBeforeMute = value.doubleValue();
                muteButton.setText("\uD83D\uDD0A");
            } else {
                muteButton.setText("\uD83D\uDD07");
            }
        });

        playPauseButton.setTooltip(new Tooltip("Riproduci / Pausa (Spazio)"));
        previousButton.setTooltip(new Tooltip("Brano precedente (Ctrl+\u2190)"));
        nextButton.setTooltip(new Tooltip("Brano successivo (Ctrl+\u2192)"));
        fullscreenButton.setTooltip(new Tooltip("Schermo intero (F11)"));
        muteButton.setText("\uD83D\uDD0A");
        muteButton.setTooltip(new Tooltip("Muto (M)"));

        for (Button button : List.of(playPauseButton, previousButton, nextButton, fullscreenButton, muteButton)) {
            button.setAccessibleRole(AccessibleRole.BUTTON);
        }
    }

    private void configureSearch() {
        searchField.setPromptText("Filtra per artista o titolo\u2026");
        searchField.setTooltip(new Tooltip("Filtra la libreria"));
        searchField.setAccessibleText("Filtra la libreria");
        searchField.textProperty().addListener((obs, old, value) -> {
            if (searchDebounce != null) {
                searchDebounce.stop();
            }
            // Il filtro viene ricalcolato una volta per raffica di tasti: con
            // decine di migliaia di elementi ricalcolarlo a ogni keypress
            // saturerebbe l'FX thread.
            searchDebounce = new PauseTransition(Duration.millis(220), () -> applyFilter(value));
            searchDebounce.play();
        });
    }

    private void configureFeedback() {
        loadingIndicator.setVisible(false);
        loadingIndicator.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        loadingIndicator.setMaxWidth(140);
        loadingIndicator.setPrefWidth(140);

        statusLabel.setText("Scegli una cartella per costruire la libreria.");
        loadingIndicator.visibleProperty().bind(busy);
        // Durante il caricamento i controlli di trasporto non devono accettare input.
        playPauseButton.disableProperty().bind(busy);
    }

    private void applyFilter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            localFilesList.setItems(library);
            return;
        }
        var filtered = FXCollections.observableArrayList(library.stream()
                .filter(t -> t.title().toLowerCase(Locale.ROOT).contains(needle)
                        || t.artist().toLowerCase(Locale.ROOT).contains(needle))
                .toList());
        localFilesList.setItems(filtered);
    }

    // =====================================================================
    // Scorciatoie da tastiera
    // =====================================================================

    /**
     * Le scorciatoie vivono sulla Scene, quindi non sono disponibili finche'
     * la radice non e' agganciata. Si registra un listener invece di assumere
     * che {@code initialize()} gia' veda la scena: non e' vero, FXMLLoader
     * istanzia il controller prima che la radice venga aggiunta alla scena.
     */
    private void bindShortcutsWhenSceneReady() {
        if (rootPane != null) {
            rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    installShortcuts(newScene);
                }
            });
            if (rootPane.getScene() != null) {
                installShortcuts(rootPane.getScene());
            }
        }
    }

    private void installShortcuts(Scene target) {
        if (shortcutsInstalled) {
            return;
        }
        shortcutsInstalled = true;

        target.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() || event.isMetaDown()) {
                switch (event.getCode()) {
                    case LEFT -> previousTrack();
                    case RIGHT -> nextTrack();
                    case F -> toggleFullscreen();
                    default -> {
                        // Combinazione non gestita: si lascia proseguire.
                    }
                }
                event.consume();
                return;
            }
            switch (event.getCode()) {
                case SPACE -> togglePlayPause();
                case F11 -> toggleFullscreen();
                case M -> toggleMute();
                case ADD, EQUALS, UP -> nudgeVolume(VOLUME_STEP);
                case SUBTRACT, HYPHEN, DOWN -> nudgeVolume(-VOLUME_STEP);
                case ESCAPE -> {
                    Stage stage = App.getPrimaryStage();
                    if (stage != null && stage.isFullScreen()) {
                        App.setFullscreen(false);
                    }
                }
                default -> {
                    // Combinazione non gestata: si lascia proseguire.
                }
            }
            event.consume();
        });
    }

    // =====================================================================
    // Caricamento della libreria
    // =====================================================================

    @FXML
    private void handleChooseFolder() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Scegli la cartella della libreria musicale");
        chooser.setInitialDirectory(
                library.isEmpty() ? new File(System.getProperty("user.home"))
                        : library.get(0).file().getParent().toFile());
        File chosen = chooser.showOpenDialog(rootPane.getScene() == null ? null : rootPane.getScene().getWindow());
        if (chosen == null) {
            return;
        }
        Path root = chosen.toPath().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            App.showError("Cartella non valida", "Il percorso selezionato non e' una cartella.");
            return;
        }
        scanLibrary(root);
    }

    /** Avvia la scansione in background, annullando un eventuale scan in corso. */
    public void scanLibrary(Path root) {
        if (libraryService != null) {
            libraryService.cancel(true);
            libraryService = null;
        }
        LibraryService service;
        try {
            service = new LibraryService(root);
        } catch (IllegalArgumentException e) {
            App.showError("Cartella non valida", "Seleziona una cartella esistente.");
            return;
        }
        libraryService = service;
        setBusy(true);
        statusLabel.setText("Scansione di " + service.getRoot().getFileName() + "\u2026");

        service.setOnSucceeded(event -> {
            List<Track> found = service.getValue();
            library.setAll(found);
            libraryCountLabel.setText("Brani in libreria: " + found.size());
            setBusy(false);
            if (found.isEmpty()) {
                statusLabel.setText("Nessun brano audio trovato in questa cartella.");
            } else {
                statusLabel.setText(found.size() + " brani caricati. Doppio click per riprodurre.");
            }
        });
        service.setOnFailed(event -> {
            setBusy(false);
            Throwable error = service.getException();
            LOG.log(Level.WARNING, "Scansione libreria fallita", error);
            statusLabel.setText("Scansione interrotta.");
            App.showError("Impossibile leggere la cartella",
                    "Controlla i permessi di lettura e riprova.\n"
                            + (error == null ? "" : describe(error)));
        });
        service.restart();
    }

    @FXML
    private void handleRefresh() {
        if (!library.isEmpty()) {
            scanLibrary(library.get(0).file().getParent());
        } else {
            handleChooseFolder();
        }
    }

    @FXML
    private void handleAddToQueue() {
        for (Track track : localFilesList.getSelectionModel().getSelectedItems()) {
            queue.add(track);
        }
        if (!queue.isEmpty()) {
            statusLabel.setText(queue.size() + " brani in coda.");
        }
    }

    @FXML
    private void handleClearQueue() {
        queue.clear();
        statusLabel.setText("Coda svuotata.");
    }

    // =====================================================================
    // Riproduzione
    // =====================================================================

    @FXML
    private void togglePlayPause() {
        if (player == null) {
            statusLabel.setText("Seleziona un brano dalla libreria per iniziare.");
            return;
        }
        if (player.getStatus() == MediaPlayer.Status.PLAYING) {
            player.pause();
        } else {
            player.play();
        }
    }

    @FXML
    private void previousTrack() {
        if (playbackHistory.isEmpty()) {
            statusLabel.setText("Nessun brano precedente.");
            return;
        }
        playTrack(playbackHistory.pop());
    }

    @FXML
    private void nextTrack() {
        if (!queue.isEmpty()) {
            playTrack(queue.remove(0));
            return;
        }
        if (currentTrack != null) {
            int index = indexInLibrary(currentTrack);
            if (index >= 0 && index + 1 < library.size()) {
                playTrack(library.get(index + 1));
                return;
            }
        }
        statusLabel.setText("Fine della coda.");
    }

    @FXML
    private void toggleMute() {
        if (volumeSlider.getValue() > 0) {
            volumeBeforeMute = volumeSlider.getValue();
            volumeSlider.setValue(0);
        } else {
            volumeSlider.setValue(volumeBeforeMute);
        }
    }

    @FXML
    private void toggleFullscreen() {
        var stage = App.getPrimaryStage();
        if (stage != null) {
            stage.setFullScreen(!stage.isFullScreen());
        }
    }

    /** Avvia la riproduzione di un brano validando percorso e leggibilita'. */
    public void playTrack(Track track) {
        if (track == null) {
            return;
        }
        if (!track.isPlayable()) {
            statusLabel.setText("Brano non disponibile: " + track.displayLabel());
            return;
        }
        if (currentTrack != null && !Objects.equals(currentTrack.file(), track.file())) {
            playbackHistory.push(currentTrack);
            if (playbackHistory.size() > RECENT_LIMIT) {
                playbackHistory.removeLast();
            }
            rememberRecent(track);
        }
        currentTrack = track;
        currentSongLabel.setText(track.title());
        currentArtistLabel.setText(track.artist().isEmpty() ? "Artista sconosciuto" : track.artist());
        currentDetailLabel.setText(track.detail());
        statusLabel.setText("Caricamento di " + track.title() + "\u2026");

        // Il file viene aperto in background: Media legge l'header e blocca.
        startMediaLoad(track);
    }

    private void startMediaLoad(Track track) {
        cancelMediaLoad();
        setBusy(true);
        try {
            mediaLoadTask = new MediaLoadTask(track.file());
        } catch (RuntimeException e) {
            setBusy(false);
            reportMediaFailure(e);
            return;
        }
        mediaLoadTask.setOnSucceeded(event -> {
            setBusy(false);
            attachMedia(mediaLoadTask.getResult(), track);
            mediaLoadTask = null;
        });
        mediaLoadTask.setOnFailed(event -> {
            setBusy(false);
            Throwable error = mediaLoadTask == null ? null : mediaLoadTask.getException();
            LOG.log(Level.WARNING, "Apertura media fallita per " + track.file(), error);
            reportMediaFailure(error);
            mediaLoadTask = null;
        });
        Thread worker = new Thread(mediaLoadTask, "cmm-media-load");
        worker.setDaemon(true);
        worker.start();
    }

    /** Attacca il media al player esistente, rilasciando il precedente. */
    private void attachMedia(Media newMedia, Track track) {
        releasePlayer();
        this.media = newMedia;
        // Un nuovo MediaPlayer per brano: riusare lo stesso player fa accumulare
        // i listener e mantiene aperti gli stream del file precedente.
        MediaPlayer newPlayer = new MediaPlayer(newMedia);
        this.player = newPlayer;

        newPlayer.setVolume(volumeSlider.getValue());
        newPlayer.setOnReady(() -> {
            updateDuration(newPlayer.getMedia().getDuration());
            newPlayer.play();
        });
        newPlayer.statusProperty().addListener(this::onStatusChanged);
        newPlayer.setOnEndOfMedia(this::onEndOfMedia);
        newPlayer.errorProperty().addListener((obs, old, error) -> {
            if (error != null) {
                reportMediaFailure(error);
            }
        });
        newMedia.errorProperty().addListener((obs, old, error) -> {
            if (error != null) {
                reportMediaFailure(error);
            }
        });

        startPolling();
        statusLabel.setText("In riproduzione: " + track.displayLabel());
    }

    private void onStatusChanged(javafx.beans.value.ObservableValue<? extends MediaPlayer.Status> obs,
                                 MediaPlayer.Status oldStatus, MediaPlayer.Status newStatus) {
        if (newStatus == MediaPlayer.Status.PLAYING) {
            playPauseButton.setText("\u23F8");   // pausa
        } else {
            playPauseButton.setText("\u25B6");   // play
        }
        if (newStatus == MediaPlayer.Status.READY && !progressBar.isDisable()) {
            updateDuration(player.getMedia() == null ? null : player.getMedia().getDuration());
        }
    }

    private void onEndOfMedia() {
        if (!queue.isEmpty()) {
            playTrack(queue.remove(0));
        } else {
            progressBar.setValue(0);
            elapsedLabel.setText("0:00");
            playPauseButton.setText("\u25B6");
            statusLabel.setText("Brano terminato.");
        }
    }

    private void rememberRecent(Track track) {
        recent.removeIf(t -> Objects.equals(t.file(), track.file()));
        recent.add(0, track);
        while (recent.size() > RECENT_LIMIT) {
            recent.remove(recent.size() - 1);
        }
    }

    private int indexInLibrary(Track track) {
        for (int i = 0; i < library.size(); i++) {
            if (Objects.equals(library.get(i).file(), track.file())) {
                return i;
            }
        }
        return -1;
    }

    // =====================================================================
    // Aggiornamento della barra di riproduzione
    // =====================================================================

    /**
     * Polling a 100 ms invece di un listener su {@code currentTimeProperty}:
     * la property emette in corrispondenza dei frame audio (decine al secondo)
     * e aggiornare la UI a quella frequenza e' lavoro sprecato. 100 ms e'
     * abbastanza fluido e non tocca l'FX thread piu' del necessario.
     */
    private void startPolling() {
        if (pollTimer != null) {
            return;
        }
        pollTimer = new Timeline(new KeyFrame(POLL_INTERVAL, event -> onPollTick()));
        pollTimer.setCycleCount(Animation.INDEFINITE);
        pollTimer.play();
    }

    private void onPollTick() {
        MediaPlayer active = player;
        if (active == null) {
            return;
        }
        if (pendingSeek) {
            // Un seek richiesto dall'utente viene committato una sola volta
            // per frame, cosi' il trascinamento non genera 100 seek al secondo.
            pendingSeek = false;
            Duration target = Duration.seconds(progressBar.getValue());
            if (!target.isNegative() && !target.equals(Duration.ZERO)) {
                active.seek(target);
            }
        }
        if (scrubbing) {
            return;
        }
        Duration current = active.getCurrentTime();
        Duration total = active.getMedia() == null ? null : active.getMedia().getDuration();
        double seconds = current == null ? 0 : Math.max(0, current.toSeconds());
        syncingFromPlayer = true;
        try {
            progressBar.setValue(seconds);
        } finally {
            syncingFromPlayer = false;
        }
        elapsedLabel.setText(MediaFiles.formatDuration(seconds));
        if (total != null) {
            updateDuration(total);
        }
    }

    private void updateDuration(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.toSeconds() <= 0) {
            durationLabel.setText("--:--");
            return;
        }
        double seconds = duration.toSeconds();
        syncingFromPlayer = true;
        try {
            progressBar.setMax(seconds);
            progressBar.setDisable(false);
        } finally {
            syncingFromPlayer = false;
        }
        durationLabel.setText(MediaFiles.formatDuration(seconds));
    }

    private void nudgeVolume(double delta) {
        volumeSlider.setValue(Math.min(1, Math.max(0, volumeSlider.getValue() + delta)));
    }

    // =====================================================================
    // Gestione risorse e ciclo di vita
    // =====================================================================

    private void setBusy(boolean value) {
        if (Platform.isFxApplicationThread()) {
            busy.set(value);
        } else {
            Platform.runLater(() -> busy.set(value));
        }
    }

    private void cancelMediaLoad() {
        if (mediaLoadTask != null) {
            mediaLoadTask.cancel(true);
            mediaLoadTask = null;
        }
    }

    /** Rilascia player e stream. Idempotente. */
    private void releasePlayer() {
        MediaPlayer active = player;
        player = null;
        if (active != null) {
            active.setOnEndOfMedia(null);
            active.setOnReady(null);
            active.statusProperty().removeListener(this::onStatusChanged);
            active.stop();
            active.dispose();     // chiude lo stream nativo e libera la porta audio
        }
        if (pollTimer != null) {
            // Il timer continua a girare e a wakeare l'FX thread ogni 100 ms
            // per niente: va fermato insieme al player.
            pollTimer.stop();
            pollTimer = null;
        }
        media = null;             // Media non ha dispose: basta togliere i riferimenti
    }

    private void reportMediaFailure(Throwable error) {
        playPauseButton.setText("\u25B6");
        progressBar.setDisable(true);
        statusLabel.setText("Riproduzione non disponibile.");
        App.showError("Impossibile riprodurre il brano",
                "Il file potrebbe essere corrotto, protetto da DRM o in un formato non supportato.\n"
                        + (error == null ? "" : describe(error)));
    }

    private static String describe(Throwable error) {
        if (error == null) {
            return "";
        }
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    /** Rilascio globale alla chiusura dell'applicazione. */
    public static void shutdownAll() {
        for (MainController controller : INSTANCES) {
            Platform.runLater(controller::releasePlayer);
        }
        INSTANCES.clear();
    }

    // =====================================================================
    // Task di supporto
    // =====================================================================

    /**
     * Apertura del file audio su thread dedicato.
     *
     * <p>{@code new Media(uri)} esegue I/O: costruirlo sull'FX thread blocca la
     * finestra per la durata dell'operazione su disco o rete.</p>
     */
    private static final class MediaLoadTask extends javafx.concurrent.Task<Media> {

        private final Path file;

        MediaLoadTask(Path file) {
            this.file = file;
        }

        @Override
        protected Media call() {
            // toUri() produce un URI percent-encoded valido anche con spazi,
            // accenti e caratteri non ASCII nel nome: evita i problemi di parsing
            // che si hanno passando a Media la stringa grezza del percorso.
            return new Media(file.toUri().toString());
        }
    }
}
