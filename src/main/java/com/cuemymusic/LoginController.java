package com.cuemymusic;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Schermata di scelta della modalita' d'accesso.
 *
 * <p>La modalita' online e' ancora uno stub: non viene effettuata nessuna
 * autenticazione reale. Le credenziali raccolte non vengono tramandate, loggate
 * o persistite da nessuna parte.</p>
 */
public class LoginController {

    private static final Logger LOG = Logger.getLogger(LoginController.class.getName());

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 256;
    private static final int MAX_USERNAME_LENGTH = 64;

    @FXML private VBox onlineLoginBox;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    @FXML
    private void initialize() {
        // Il primo campo utile riceve il focus: dopo il login l'utente non deve
        // dover cliccare per iniziare a digitare.
        usernameField.requestFocus();
        // Invio da username o password equivale al click su "Accedi".
        usernameField.setOnKeyPressed(this::submitOnEnter);
        passwordField.setOnKeyPressed(this::submitOnEnter);
    }

    @FXML
    private void handleLocalAccess() {
        navigateToMain("Modalita' locale attivata.");
    }

    @FXML
    private void handleOnlineSync() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showStatus("Inserisci nome utente e password.", true);
            return;
        }
        if (username.length() > MAX_USERNAME_LENGTH) {
            showStatus("Nome utente troppo lungo.", true);
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            showStatus("La password deve avere almeno " + MIN_PASSWORD_LENGTH + " caratteri.", true);
            return;
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            showStatus("Password troppo lunga.", true);
            return;
        }

        // La connessione non e' ancora implementata: si annuncia e si entra in
        // modalita' locale. Nessuna credenziale viene loggata o conservata.
        showStatus("Sincronizzazione non ancora disponibile: ingresso in modalita' locale.", false);
        // Il campo password viene svuotato subito: non serve a nulla dopo
        // l'operazione e mantenerlo in memoria allunga la sua esposizione.
        passwordField.clear();
        navigateToMain(null);
    }

    private void submitOnEnter(javafx.scene.input.KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            handleOnlineSync();
            event.consume();
        }
    }

    /**
     * Passa alla schermata principale, passando dal fullscreen perche' il player
     * e' pensato per la riproduzione a schermo pieno.
     */
    private void navigateToMain(String message) {
        try {
            App.setRoot("main");
            App.setFullscreen(true);
            if (message != null) {
                LOG.info(message);
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.SEVERE, "Navigazione alla schermata principale fallita", e);
            showStatus("Impossibile aprire il player. Riavvia l'applicazione.", true);
            App.showError("Errore di navigazione",
                    "La schermata principale non e' disponibile.\n"
                            + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-error");
        if (isError) {
            statusLabel.getStyleClass().add("status-error");
        }
    }
}
