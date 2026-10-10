package com.cuemymusic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Utilita' di sicurezza e normalizzazione per i percorsi dei file audio.
 *
 * <p>Tutta la logica e' deliberatamente privata di effetti collaterali e senza I/O
 * pesante: i controlli su dimensione e realpath sono effettuati dal
 * {@link LibraryService} su un thread di background, mentre questa classe si occupa
 * solo di validazione e di formattazione sicura per la UI.</p>
 */
final class MediaFiles {

    /** Estensioni accettate come brano riproducibile. Lista chiusa: default deny. */
    static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "m4a", "m4b", "aac", "wav", "flac", "ogg", "oga", "opus", "wma", "mp4", "webm");

    /** Soglia oltre la quale un file e' considerato sospetto (2 GiB). */
    static final long MAX_FILE_BYTES = 2L * 1024L * 1024L * 1024L;

    /** Limite di lunghezza della stringa grezza accettata come percorso. */
    private static final int MAX_RAW_PATH_LENGTH = 4096;

    /** Limite di lunghezza del testo mostrato nelle liste. */
    private static final int MAX_DISPLAY_LENGTH = 120;

    private MediaFiles() {
        // utility class
    }

    /**
     * Converte una stringa grezza in un percorso audio valido, senza seguire i link simbolici.
     *
     * @param raw percorso grezzo (da file chooser, da drag & drop o da config)
     * @return il percorso normalizzato, oppure {@link Optional#empty()} se non valido
     */
    static Optional<Path> toSafeAudioPath(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_RAW_PATH_LENGTH) {
            return Optional.empty();
        }
        // Il byte nullo e' invalido in un percorso POSIX e nel parsing NTFS/Ansi:
        // rifiutarlo esplicitamente evita eccezioni fuoriuscite dai costruttori.
        if (trimmed.indexOf('\0') >= 0) {
            return Optional.empty();
        }
        try {
            Path candidate = Path.of(trimmed).toAbsolutePath().normalize();
            return isSupportedAudio(candidate) ? Optional.of(candidate) : Optional.empty();
        } catch (InvalidPathException | SecurityException e) {
            return Optional.empty();
        }
    }

    /** {@code true} se il percorso ha un'estensione presente in {@link #AUDIO_EXTENSIONS}. */
    static boolean isSupportedAudio(Path path) {
        if (path == null) {
            return false;
        }
        String name = path.getFileName() == null ? null : path.getFileName().toString();
        return name != null && hasAudioExtension(name);
    }

    /** Estensione in minuscolo senza il punto; stringa vuota se il nome non ne ha. */
    static String extensionOf(Path path) {
        if (path == null) {
            return "";
        }
        Path fileName = path.getFileName();
        if (fileName == null) {
            return "";
        }
        String name = fileName.toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean hasAudioExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return false;
        }
        return AUDIO_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * Verifica che {@code candidate} sia contenuto in {@code root} dopo la risoluzione
     * dei link simbolici. E' la barriera contro la directory traversal: senza questo
     * controllo un link simbolico verso {@code /etc} entrerebbe in libreria.
     *
     * <p>Non solleva eccezioni: in caso di errore e' conservativo e restituisce
     * {@code false}.</p>
     */
    static boolean isWithinRoot(Path root, Path candidate) {
        if (root == null || candidate == null) {
            return false;
        }
        try {
            Path realRoot = root.toRealPath();
            Path realCandidate = candidate.toRealPath();
            return !realCandidate.equals(realRoot) && realCandidate.startsWith(realRoot);
        } catch (IOException | RuntimeException e) {
            // SecurityException e' una RuntimeException: accorparla evita un
            // multi-catch con alternative imparentate, che il compilatore rifiuta.
            return false;
        }
    }

    /** {@code true} se il file esiste, e' regolare e leggibile. */
    static boolean isReadableRegularFile(Path path) {
        if (path == null) {
            return false;
        }
        try {
            return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(path);
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * Sanitizza un testo destinato a essere mostrato in UI.
     *
     * <p>I caratteri pericolosi non <i>svaniscono</i>: diventano un separatore.
     * Un file chiamato {@code "track\u202Egnp.exe.mp3"} mostra in interfaccia
     * {@code "track gnp.exe.mp3"} e non {@code "trackgnp.exe.mp3"}: lo spazio
     * rende evidente che il nome e' composto, invece di ricomporlo di nascosto
     * (attacco "Trojan Source").</p>
     *
     * <p>Sono filtrati i caratteri di controllo C0/C1, le direzionali Unicode
     * (U+202A-U+202E, U+2066-U+2069, U+200E/U+200F) e il soft hyphen; gli spazi
     * ripetuti sono collassati e il testo e' troncato alla lunghezza massima.</p>
     */
    static String sanitizeForDisplay(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(raw.length(), MAX_DISPLAY_LENGTH));
        int pendingSpaces = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (isUnsafeForDisplay(c)) {
                pendingSpaces = out.isEmpty() ? 0 : 1;
                continue;
            }
            if (c == ' ' || c == '\t') {
                pendingSpaces = 1;
                continue;
            }
            if (pendingSpaces > 0 && !out.isEmpty()) {
                out.append(' ');
            }
            pendingSpaces = 0;
            out.append(c);
            if (out.length() >= MAX_DISPLAY_LENGTH) {
                break;
            }
        }
        if (out.length() >= MAX_DISPLAY_LENGTH) {
            out.setLength(MAX_DISPLAY_LENGTH);
            out.append('\u2026');
        }
        return out.toString().trim();
    }

    private static boolean isUnsafeForDisplay(char c) {
        if (c < 0x20 || c == 0x7F) {
            return true;                       // C0 + DEL
        }
        if (c >= 0x80 && c <= 0x9F) {
            return true;                       // C1
        }
        return switch (c) {
            case '\u200E', '\u200F',            // LRM / RLM
                 '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',  // embedding/override bidi
                 '\u2066', '\u2067', '\u2068', '\u2069',            // isolates bidi
                 '\u00AD'                                        // soft hyphen
                -> true;
            default -> false;
        };
    }

    /** Dimensione in byte formattata per la UI (es. "4,2 MB"). */
    static String formatSize(long bytes) {
        if (bytes < 0) {
            return "?";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"kB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    /** Secondi -> {@code m:ss} (o {@code h:mm:ss} oltre un'ora). Input non finito -> "--:--". */
    static String formatDuration(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0) {
            return "--:--";
        }
        long total = (long) Math.floor(seconds);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long secs = total % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }
}
