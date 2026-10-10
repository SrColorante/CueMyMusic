package com.cuemymusic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javafx.fxml.FXML;

/**
 * Contratto fra FXML e controller, verificato senza caricare l'FXML (e quindi
 * senza toolkit).
 *
 * <p>Il commit che ha introdotto la libreria locale non compilava: usava
 * {@code AccessibleRole.LIST}, {@code Service.cancel(boolean)},
 * {@code Task.getResult()} e altri metodi che in JavaFX 21 non esistono. Questo
 * test non puo' prendere quel tipo di errore, ma prende il suo gemello
 * silenzioso: un {@code fx:id} o un {@code onAction} che punta a qualcosa che
 * nel controller non c'e' piu'. Senza questo controllo l'errore comparirebbe
 * solo a runtime, come {@code LoadException} all'avvio.</p>
 */
class FxmlContractTest {

    private static final Pattern FX_ID = Pattern.compile("fx:id=\"([A-Za-z0-9_]+)\"");
    private static final Pattern ON_ACTION = Pattern.compile("onAction=\"#([A-Za-z0-9_]+)\"");
    private static final Pattern STYLE_CLASS = Pattern.compile("styleClass=\"([^\"]+)\"");

    private static final List<String> SCHERMATE = List.of("login", "main");

    private static String fxml(String name) {
        try (var in = FxmlContractTest.class.getResourceAsStream(
                "/com/cuemymusic/fxml/" + name + ".fxml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> matches(String text, Pattern pattern) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static Set<String> fxmlFields(Class<?> controller) {
        Set<String> out = new LinkedHashSet<>();
        for (Field f : controller.getDeclaredFields()) {
            if (f.isAnnotationPresent(FXML.class)) {
                out.add(f.getName());
            }
        }
        return out;
    }

    private static Set<String> fxmlMethods(Class<?> controller) {
        Set<String> out = new LinkedHashSet<>();
        for (Method m : controller.getDeclaredMethods()) {
            if (m.isAnnotationPresent(FXML.class)) {
                out.add(m.getName());
            }
        }
        return out;
    }

    @Nested
    @DisplayName("fx:id dell'FXML")
    class Identificatori {

        @Test
        @DisplayName("ogni fx:id ha il campo @FXML corrispondente")
        void ogniIdHaUnCampo() {
            for (String schermata : SCHERMATE) {
                String fxml = fxml(schermata);
                Set<String> ids = matches(fxml, FX_ID);
                Set<String> campi = fxmlFields(controllerOf(schermata));
                Set<String> orfani = new LinkedHashSet<>(ids);
                orfani.removeAll(campi);
                assertTrue(orfani.isEmpty(),
                        schermata + ": fx:id senza campo @FXML -> " + orfani);
            }
        }

        @Test
        @DisplayName("nessun campo @FXML resta dichiarato e mai usato nell'FXML")
        void nessunCampoMorto() {
            for (String schermata : SCHERMATE) {
                String fxml = fxml(schermata);
                Set<String> ids = matches(fxml, FX_ID);
                Set<String> campi = fxmlFields(controllerOf(schermata));
                Set<String> inutilizzati = new LinkedHashSet<>(campi);
                inutilizzati.removeAll(ids);
                assertTrue(inutilizzati.isEmpty(),
                        schermata + ": campo @FXML senza fx:id -> " + inutilizzati);
            }
        }
    }

    @Nested
    @DisplayName("onAction dell'FXML")
    class Handler {

        @Test
        @DisplayName("ogni onAction ha un metodo nel controller")
        void ogniHandlerEsiste() {
            for (String schermata : SCHERMATE) {
                String fxml = fxml(schermata);
                Set<String> handlers = matches(fxml, ON_ACTION);
                Set<String> metodi = new LinkedHashSet<>();
                for (Method m : controllerOf(schermata).getDeclaredMethods()) {
                    metodi.add(m.getName());
                }
                Set<String> mancanti = new LinkedHashSet<>(handlers);
                mancanti.removeAll(metodi);
                assertTrue(mancanti.isEmpty(),
                        schermata + ": onAction senza metodo -> " + mancanti);
            }
        }

        @Test
        @DisplayName("ogni metodo @FXML senza parametri e' raggiungibile da un nodo")
        void nessunMetodoIrraggiungibile() {
            for (String schermata : SCHERMATE) {
                String fxml = fxml(schermata);
                Set<String> handlers = matches(fxml, ON_ACTION);
                Set<String> metodi = new LinkedHashSet<>(fxmlMethods(controllerOf(schermata)));
                // initialize() lo chiama FXMLLoader, non un nodo.
                metodi.remove("initialize");
                Set<String> irraggiungibili = new LinkedHashSet<>(metodi);
                irraggiungibili.removeAll(handlers);
                assertTrue(irraggiungibili.isEmpty(),
                        schermata + ": metodo @FXML non agganciato -> " + irraggiungibili);
            }
        }
    }

    @Nested
    @DisplayName("Coerenza delle risorse")
    class Risorse {

        @Test
        @DisplayName("ogni styleClass usata ha una regola nel foglio di stile")
        void ogniClasseHaUnaRegola() {
            String css = read("/com/cuemymusic/css/styles.css");
            Set<String> usate = new LinkedHashSet<>();
            for (String schermata : SCHERMATE) {
                for (String gruppo : matches(fxml(schermata), STYLE_CLASS)) {
                    for (String c : gruppo.split(",")) {
                        if (!c.trim().isEmpty()) {
                            usate.add(c.trim());
                        }
                    }
                }
            }
            // classi assegnate da codice: il testo dell'FXML non le vede
            usate.addAll(Set.of("muted-label", "track-cell", "track-title",
                    "track-subtitle", "status-error", "root-pane"));
            List<String> mancanti = new ArrayList<>();
            for (String c : usate) {
                if (!css.contains("." + c)) {
                    mancanti.add(c);
                }
            }
            assertTrue(mancanti.isEmpty(), "styleClass senza regola CSS -> " + mancanti);
        }

        @Test
        @DisplayName("i file FXML e il CSS esistono tra le risorse")
        void risorsePresenti() {
            for (String path : new String[]{
                    "/com/cuemymusic/fxml/login.fxml",
                    "/com/cuemymusic/fxml/main.fxml",
                    "/com/cuemymusic/css/styles.css",
                    "/com/cuemymusic/fonts/Comfortaa-Regular.ttf"}) {
                assertTrue(FxmlContractTest.class.getResource(path) != null,
                        "risorsa assente: " + path);
            }
        }
    }

    private static String read(String path) {
        try (var in = FxmlContractTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Class<?> controllerOf(String schermata) {
        return schermata.equals("login") ? LoginController.class : MainController.class;
    }
}
