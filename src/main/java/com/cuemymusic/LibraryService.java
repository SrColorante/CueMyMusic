package com.cuemymusic;

import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Scansione della libreria musicale eseguita interamente su un thread di background.
 *
 * <p>Esiste per due motivi: <b>performance</b> (attraversare un disco e fare
 * {@code stat} su ogni file bloccherebbe l'FX thread e congelerebbero la finestra)
 * e <b>sicurezza</b> (la traversal applica i vincoli di contenimento e ignora i
 * link simbolici, cosi' un {@code symlink} non puo' far uscire la scansione dalla
 * cartella scelta dall'utente).</p>
 *
 * <p>La classe e' progettata per essere istanziata e avviata dall'FX thread ma
 * eseguita su un worker dedicato; la cancellazione e' cooperativa e controllata a
 * ogni passo della visita, con un tetto massimo di file per evitare esaurimento
 * di memoria su alberi patologici.</p>
 *
 * <p>La visita vive in una classe {@code Task} dedicata invece che in un metodo
 * privato del {@link Service}: dentro il metodo del servizio {@code isCancelled()}
 * non e' risolvibile, perche' quell'ultimo appartiene al {@code Task} che ancora
 * non esiste quando il metodo viene compilato.</p>
 */
public final class LibraryService extends Service<List<Track>> {

    /** Tetto massimo di brani raccolti: protegge la memoria su alberi enormi. */
    static final int MAX_TRACKS = 20_000;

    /** Profondita' massima di discesa dalla root. */
    static final int MAX_DEPTH = 16;

    /** Numero massimo di file visitati (inclusi quelli scartati). */
    static final int MAX_VISITS = 200_000;

    private final Path root;

    /**
     * @param root cartella radice della scansione
     * @throws IllegalArgumentException se {@code root} non e' una directory
     */
    public LibraryService(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("Cartella radice non valida");
        }
        this.root = root.toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    /** La visita e' eseguita da {@link Service} su un thread separato. */
    @Override
    protected Task<List<Track>> createTask() {
        return new LibraryScanTask(root);
    }

    /**
     * Lavoro effettivo della scansione.
     *
     * <p>Estendendo {@link Task} l'{@code isCancelled()} usato durante la visita
     * appartiene a questa classe: la cancellazione e' quindi cooperativa e
     * interrompe la traversal senza lasciare thread appesi.</p>
     */
    private static final class LibraryScanTask extends Task<List<Track>> {

        private final Path root;

        LibraryScanTask(Path root) {
            this.root = root;
        }

        @Override
        protected List<Track> call() throws Exception {
            Path realRoot = root.toRealPath();
            if (!Files.isDirectory(realRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("La cartella selezionata non e' piu' disponibile");
            }

            List<Track> tracks = new ArrayList<>(256);
            // NOFOLLOW_LINKS: i link simbolici vengono visitati come file non regolari
            // e quindi scartati. Evita sia la directory traversal sia i cicli infiniti.
            EnumSet<FileVisitOption> options = EnumSet.noneOf(FileVisitOption.class);

            Files.walkFileTree(realRoot, options, MAX_DEPTH, new FileVisitor<>() {
                private int visits;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (isCancelled() || visits > MAX_VISITS) {
                        return FileVisitResult.TERMINATE;
                    }
                    // Salta le cartelle nascoste (.git, .cache, ...): rumore per l'utente
                    // e costo inutile, senza perdita di valore per un player musicale.
                    if (dir.getNameCount() > realRoot.getNameCount() && isHidden(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isCancelled() || visits > MAX_VISITS || tracks.size() >= MAX_TRACKS) {
                        return FileVisitResult.TERMINATE;
                    }
                    visits++;
                    try {
                        // attrs arriva gia' senza seguire i link: isRegularFile e' false
                        // per un symlink, quindi il controllo e' una seconda barriera.
                        if (!attrs.isRegularFile() || !MediaFiles.isSupportedAudio(file)) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (attrs.size() <= 0 || attrs.size() > MediaFiles.MAX_FILE_BYTES) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (!MediaFiles.isReadableRegularFile(file)) {
                            return FileVisitResult.CONTINUE;
                        }
                        // Difesa finale contro l'evasione dalla root tramite link.
                        if (!MediaFiles.isWithinRoot(realRoot, file)) {
                            return FileVisitResult.CONTINUE;
                        }
                        tracks.add(toTrack(file, attrs.size()));
                    } catch (SecurityException e) {
                        // Un file non leggibile non deve abortire l'intera scansione.
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Permesso negato su un singolo file: si prosegue con gli altri.
                    return isCancelled() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });

            tracks.sort(Comparator
                    .comparing(Track::artist, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Track::title, String.CASE_INSENSITIVE_ORDER));
            return List.copyOf(tracks);
        }

        private static Track toTrack(Path file, long sizeBytes) {
            String rawName = file.getFileName().toString();
            int dot = rawName.lastIndexOf('.');
            String base = dot > 0 ? rawName.substring(0, dot) : rawName;

            String artist = "";
            Path parent = file.getParent();
            if (parent != null && parent.getNameCount() > 0) {
                Path name = parent.getFileName();
                if (name != null) {
                    artist = name.toString();
                }
            }

            String detail = MediaFiles.extensionOf(file).toUpperCase(Locale.ROOT)
                    + " \u00B7 " + MediaFiles.formatSize(sizeBytes);
            return new Track(file, base, artist, detail);
        }

        private static boolean isHidden(Path path) {
            Path name = path.getFileName();
            return name != null && name.toString().startsWith(".");
        }
    }
}
