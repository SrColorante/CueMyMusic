package com.cuemymusic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javafx.concurrent.Task;

/**
 * Scansione della libreria.
 *
 * <p>{@code LibraryService} estende {@code Service}, quindi avviarlo richiede il
 * toolkit JavaFX. Questi test non hanno un display a disposizione, percio'
 * costruiscono il {@code Task} con {@code createTask()} e ne invocano
 * {@code call()} direttamente: e' esattamente il codice che gira sul worker,
 * senza la parte di scheduling.</p>
 */
class LibraryServiceTest {

    @TempDir
    Path root;

    private Path artistaA;
    private Path artistaB;

    @BeforeEach
    void buildLibrary() throws IOException {
        artistaA = Files.createDirectories(root.resolve("ArtistOne"));
        artistaB = Files.createDirectories(root.resolve("ArtistTwo"));
        writeAudio(artistaA.resolve("primo.mp3"), 1_024);
        writeAudio(artistaA.resolve("secondo.mp3"), 1_024);
        writeAudio(artistaB.resolve("terzo.mp3"), 2_048);
        writeAudio(artistaB.resolve("quarto.wav"), 4_096);
        Files.writeString(root.resolve("leggimi.txt"), "non audio");
        // NOFOLLOW_LINKS: il link non viene ne' attraversato ne' seguito, quindi
        // ArtistOne non deve comparire due volte ne' comparire con questo percorso.
        Files.createSymbolicLink(root.resolve("link_artista"), artistaA);
    }

    @AfterEach
    void cleanup() {
        // @TempDir di JUnit pulisce da solo, ma un symlink verso una cartella
        // interna puo' confondere la cancellazione ricorsiva: lo si toglie a mano.
        try {
            Files.deleteIfExists(root.resolve("link_artista"));
        } catch (IOException ignored) {
            // e' meglio che la cancellazione della temp dir fallisca per questo
        }
    }

    @Test
    @DisplayName("trova tutti e quattro i file audio e niente altro")
    void trovaSoloAudio() throws Exception {
        List<Track> found = scan(root);
        assertEquals(4, found.size(), found.toString());
    }

    @Test
    @DisplayName("il file non audio nella root e' scartato")
    void scartaNonAudio() throws Exception {
        assertTrue(scan(root).stream().noneMatch(t -> t.title().contains("leggimi")), scan(root).toString());
    }

    @Test
    @DisplayName("le cartelle nascoste non sono attraversate")
    void saltaNascoste() throws Exception {
        Path hidden = Files.createDirectories(root.resolve(".cache"));
        writeAudio(hidden.resolve("nascosto.mp3"), 512);
        List<Track> found = scan(root);
        assertEquals(4, found.size(), found.toString());
        assertTrue(found.stream().noneMatch(t -> t.title().contains("nascosto")), found.toString());
    }

    @Test
    @DisplayName("un link simbolico a una cartella non viene seguito: nessun brano duplicato")
    void nonSegueSimbolici() throws Exception {
        List<Track> found = scan(root);
        long primo = found.stream().filter(t -> t.title().equals("primo")).count();
        assertEquals(1, primo, found.toString());
        assertTrue(found.stream().noneMatch(t -> t.file().toString().contains("link_artista")),
                found.toString());
    }

    @Test
    @DisplayName("un link simbolico verso l'esterno della root non entra in libreria")
    void symlinkEsterno() throws Exception {
        Path esterna = Files.createTempDirectory("cmm-esterna");
        writeAudio(esterna.resolve("fuori.mp3"), 512);
        Files.createSymbolicLink(root.resolve("escap"), esterna);
        try {
            assertTrue(scan(root).stream().noneMatch(t -> t.title().equals("fuori")),
                    scan(root).toString());
        } finally {
            Files.deleteIfExists(root.resolve("escap"));
        }
    }

    @Test
    @DisplayName("l'artista e' dedotto dalla cartella che contiene il file")
    void artistaDallaCartella() throws Exception {
        Map<String, List<Track>> perArtista = scan(root).stream()
                .collect(Collectors.groupingBy(Track::artist));
        assertEquals(2, perArtista.size(), perArtista.toString());
        assertEquals(2, perArtista.getOrDefault("ArtistOne", List.of()).size(), perArtista.toString());
        assertEquals(2, perArtista.getOrDefault("ArtistTwo", List.of()).size(), perArtista.toString());
    }

    @Test
    @DisplayName("l'ordinamento e' per artista e poi per titolo, senza distinguere maiuscole")
    void ordinamento() throws Exception {
        List<Track> found = scan(root);
        assertEquals("ArtistOne", found.get(0).artist(), found.toString());
        assertEquals("ArtistTwo", found.get(found.size() - 1).artist(), found.toString());
        List<String> titoli = found.stream().map(Track::title).toList();
        // Il confronto e' sulla coppia artista/titolo, non sul solo titolo:
        // "quarto" sta in ArtistTwo e deve venire dopo "secondo" di ArtistOne.
        List<String> coppie = found.stream().map(t -> t.artist() + "/" + t.title()).toList();
        List<String> ordinate = coppie.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        assertEquals(ordinate, coppie, coppie.toString());
    }

    @Test
    @DisplayName("il titolo perde l'estensione; il dettaglio la recupera con formato e dimensione")
    void metadati() throws Exception {
        Track t = scan(root).stream().filter(x -> x.title().equals("primo")).findFirst().orElseThrow();
        assertEquals("MP3", t.detail().substring(0, 3), t.detail());
        assertTrue(t.detail().contains("\u00B7"), t.detail());
        assertTrue(t.detail().contains("1.0 kB"), t.detail());
        assertEquals("ArtistOne \u00B7 MP3 \u00B7 1.0 kB", t.subtitle(), t.subtitle());
        assertEquals("primo \u2014 ArtistOne", t.displayLabel(), t.displayLabel());
    }

    @Test
    @DisplayName("un file da zero byte e' ignorato invece di comparire vuoto")
    void fileVuoto() throws Exception {
        Files.createFile(artistaB.resolve("vuoto.mp3"));   // davvero 0 byte
        assertTrue(scan(root).stream().noneMatch(t -> t.title().equals("vuoto")), scan(root).toString());
    }

    @Test
    @DisplayName("la lista restituita e' immutabile: il chiamante non puo' corrompere lo stato")
    void listaImmutabile() throws Exception {
        List<Track> found = scan(root);
        assertThrows(UnsupportedOperationException.class, () -> found.remove(0));
    }

    @Test
    @DisplayName("ogni brano trovato risulta riproducibile")
    void tuttiRiproducibili() throws Exception {
        List<Track> found = scan(root);
        assertTrue(found.stream().allMatch(Track::isPlayable), found.toString());
    }

    @Test
    @DisplayName("un titolo con caratteri bidi non arriva in UI cosi' com'e'")
    void titoloSanificato() throws Exception {
        writeAudio(artistaB.resolve("m\u202Egnp.mp3"), 512);
        Track t = scan(root).stream().filter(x -> x.title().contains("gnp")).findFirst().orElseThrow();
        assertFalse(t.title().contains("\u202E"), t.title());
    }

    @Test
    @DisplayName("una root che non esiste fa fallire la scansione, non restituire una lista vuota")
    void rootMancante() throws Exception {
        IOException ex = assertThrows(IOException.class, () -> scan(root.resolve("non-esiste")));
        assertFalse(ex.getMessage() == null || ex.getMessage().isBlank(), String.valueOf(ex.getMessage()));
    }

    @Test
    @DisplayName("una root che e' un file e non una cartella e' rifiutata con un messaggio chiaro")
    void rootNonCartella() throws Exception {
        Path file = root.resolve("brano.mp3");
        Files.write(file, new byte[]{1, 2, 3});
        IOException ex = assertThrows(IOException.class, () -> scan(file));
        assertTrue(ex.getMessage().contains("non e' piu' disponibile"), ex.getMessage());
    }

    // ------------------------------------------------------------------ utilita'

    /**
     * Esegue il lavoro del worker senza {@code Service}: il risultato e le
     * eccezioni sono quelli veri della scansione.
     *
     * <p>{@link java.lang.reflect.InvocationTargetException} e' un artefatto
     * della reflection: va disfatto, altrimenti il chiamante vede un errore che
     * la scansione non ha mai prodotto.</p>
     */
    private List<Track> scan(Path root) throws Exception {
        LibraryService service = new LibraryService(root);
        Method createTask = LibraryService.class.getDeclaredMethod("createTask");
        createTask.setAccessible(true);
        Task<List<Track>> task = (Task<List<Track>>) createTask.invoke(service);
        Method call = Task.class.getDeclaredMethod("call");
        call.setAccessible(true);
        Object result;
        try {
            result = call.invoke(task);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof Exception ex ? ex : e;
        }
        @SuppressWarnings("unchecked")
        List<Track> found = (List<Track>) result;
        return found;
    }

    private static void writeAudio(Path file, int size) throws IOException {
        Files.write(file, new byte[Math.max(1, size)]);
    }
}
