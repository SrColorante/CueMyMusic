package com.cuemymusic;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.nio.file.Path;

/**
 * Brano caricato in libreria: i metadati sono gia' calcolati dal
 * {@link LibraryService} su thread di background, cosi' le celle della lista
 * non toccano mai il filesystem (che e' I/O bloccante) dall'FX thread.
 *
 * @param file   percorso normalizzato e validato
 * @param title  nome del brano, gia' sanificato per la UI
 * @param artist artista dedotto dalla cartella contenente, gia' sanificato
 * @param detail dettaglio secondario (formato + dimensione)
 */
public record Track(Path file, String title, String artist, String detail) {

    public Track {
        // difesa in profondita: anche se un Track venisse costruito a mano
        // con dati sporchi, la UI non mostra mai testo non sanificato.
        file = file == null ? null : file.toAbsolutePath().normalize();
        title = MediaFiles.sanitizeForDisplay(title);
        artist = MediaFiles.sanitizeForDisplay(artist);
        detail = MediaFiles.sanitizeForDisplay(detail);
    }

    /** Riga secondaria: artista se noto, altrimenti il dettaglio tecnico. */
    public String subtitle() {
        if (!artist.isEmpty()) {
            return detail.isEmpty() ? artist : artist + " \u00B7 " + detail;
        }
        return detail;
    }

    /** Label monolinea usata come fallback e come testo accessibile. */
    public String displayLabel() {
        if (artist.isEmpty()) {
            return title;
        }
        return title + " \u2014 " + artist;
    }

    public StringProperty titleProperty() {
        return new SimpleStringProperty(title);
    }

    public StringProperty artistProperty() {
        return new SimpleStringProperty(artist);
    }

    /** {@code true} se il brano e' utilizzabile per la riproduzione. */
    public boolean isPlayable() {
        return file != null && MediaFiles.isSupportedAudio(file) && MediaFiles.isReadableRegularFile(file);
    }
}
