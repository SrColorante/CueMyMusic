package com.cuemymusic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Regole di {@link MediaFiles}: sono funzioni pure, quindi verificabili senza
 * avviare il toolkit JavaFX (qui sotto test non esiste alcun display).
 */
class MediaFilesTest {

    @Nested
    @DisplayName("Estensioni accettate")
    class Estensioni {

        @Test
        @DisplayName("mp3, m4a, wav, flac, ogg, opus sono brani riproducibili")
        void estensioniValide() {
            for (String name : new String[]{"a.mp3", "a.M4A", "a.wav", "a.flac", "a.ogg",
                    "a.opus", "a.webm", "a.m4b", "a.aac", "a.oga", "a.wma", "a.mp4"}) {
                assertTrue(MediaFiles.isSupportedAudio(Path.of(name)), name);
            }
        }

        @Test
        @DisplayName("listra chiusa: txt, exe, pdf e senza estensione sono rifiutati")
        void estensioniInValide() {
            for (String name : new String[]{"a.txt", "a.exe", "a.pdf", "a", "a.", ".mp3", "a.mp3.bak"}) {
                assertFalse(MediaFiles.isSupportedAudio(Path.of(name)), name);
            }
        }

        @Test
        @DisplayName("path null non fa esplodere il controllo")
        void pathNull() {
            assertFalse(MediaFiles.isSupportedAudio(null));
            assertEquals("", MediaFiles.extensionOf(null));
        }

        @Test
        @DisplayName("extensionOf restituisce l'estensione in minuscolo")
        void estensioneEstratta() {
            assertEquals("mp3", MediaFiles.extensionOf(Path.of("a.MP3")));
            assertEquals("", MediaFiles.extensionOf(Path.of("a")));
            assertEquals("", MediaFiles.extensionOf(Path.of("a.")));
        }
    }

    @Nested
    @DisplayName("Sanificazione del testo mostrato")
    class Sanificazione {

        @Test
        @DisplayName("una direzionale bidi non puo' camuffare il nome reale (Trojan Source)")
        void rimuoveBidi() {
            String sporco = "m\u202Egnp.mp3";
            String pulito = MediaFiles.sanitizeForDisplay(sporco);
            assertFalse(pulito.contains("\u202E"), pulito);
            assertTrue(pulito.indexOf('g') < pulito.length(), pulito);
        }

        @Test
        @DisplayName("i caratteri di controllo C0 e C1 diventano un separatore, non svaniscono")
        void rimuoveControlli() {
            // "a b c" e non "abc": unire le parti di nascosto e' esattamente
            // cio' che la sanificazione deve impedire.
            assertEquals("a b c", MediaFiles.sanitizeForDisplay("a\u0001b\u0002c"));
            assertEquals("a b c", MediaFiles.sanitizeForDisplay("a\u0085b\u009fc"));
            assertEquals("a b", MediaFiles.sanitizeForDisplay("a\u202Eb"));
        }

        @Test
        @DisplayName("gli spazi ripetuti sono collassati")
        void collassaSpazi() {
            assertEquals("a b c", MediaFiles.sanitizeForDisplay("a   b\t c"));
        }

        @Test
        @DisplayName("il testo lungo e' troncato con l'ellissi")
        void tronca() {
            String out = MediaFiles.sanitizeForDisplay("x".repeat(500));
            assertEquals(121, out.length());
            assertTrue(out.endsWith("\u2026"), out);
        }

        @Test
        @DisplayName("null e stringa vuota restano vuoti")
        void vuoti() {
            assertEquals("", MediaFiles.sanitizeForDisplay(null));
            assertEquals("", MediaFiles.sanitizeForDisplay(""));
        }
    }

    @Nested
    @DisplayName("Formattatori della barra di riproduzione")
    class Formattatori {

        @Test
        @DisplayName("durata in secondi -> m:ss, poi h:mm:ss")
        void durate() {
            assertEquals("0:00", MediaFiles.formatDuration(0));
            assertEquals("0:09", MediaFiles.formatDuration(9.4));
            assertEquals("1:01", MediaFiles.formatDuration(61));
            assertEquals("59:59", MediaFiles.formatDuration(3599));
            assertEquals("1:00:00", MediaFiles.formatDuration(3600));
            assertEquals("1:01:01", MediaFiles.formatDuration(3661));
        }

        @Test
        @DisplayName("durata non ancora nota -> --:--")
        void durataSconosciuta() {
            assertEquals("--:--", MediaFiles.formatDuration(Double.NaN));
            assertEquals("--:--", MediaFiles.formatDuration(Double.POSITIVE_INFINITY));
            assertEquals("--:--", MediaFiles.formatDuration(-1));
        }

        @Test
        @DisplayName("dimensione in byte -> unita' leggibile")
        void dimensioni() {
            assertEquals("0 B", MediaFiles.formatSize(0));
            assertEquals("1 B", MediaFiles.formatSize(1));
            assertEquals("1.0 kB", MediaFiles.formatSize(1024));
            assertEquals("1.5 kB", MediaFiles.formatSize(1536));
            assertEquals("1.0 MB", MediaFiles.formatSize(1024L * 1024L));
            assertEquals("?", MediaFiles.formatSize(-1));
        }
    }

    @Nested
    @DisplayName("Percorsi accettati")
    class Percorsi {

        @Test
        @DisplayName("un file audio valido supera il controllo")
        void percorsoValido() {
            Optional<Path> p = MediaFiles.toSafeAudioPath("/tmp/brano.mp3");
            assertTrue(p.isPresent());
            assertEquals("/tmp/brano.mp3", p.get().toString());
        }

        @Test
        @DisplayName("null, vuoto, troppo lungo o con NUL sono rifiutati")
        void percorsiInValidi() {
            assertFalse(MediaFiles.toSafeAudioPath(null).isPresent());
            assertFalse(MediaFiles.toSafeAudioPath("   ").isPresent());
            assertFalse(MediaFiles.toSafeAudioPath("a\u0000b.mp3").isPresent());
            assertFalse(MediaFiles.toSafeAudioPath("x".repeat(5000) + ".mp3").isPresent());
        }

        @Test
        @DisplayName("un percorso relativo e' normalizzato ad assoluto")
        void normalizzazione() {
            Optional<Path> p = MediaFiles.toSafeAudioPath("./brano.mp3");
            assertTrue(p.isPresent());
            assertTrue(p.get().isAbsolute(), p.get().toString());
        }
    }
}
