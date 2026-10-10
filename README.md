# CueMyMusic

> Progetto in mostra su **[cristianrenosto.party/projects](https://cristianrenosto.party/projects)**

> Player musicale desktop in JavaFX, orientato al concetto di *cue point* e alle
> playlist da DJ (stile "walk it / talk it").

**Stato: prototipo funzionante a metà.** La libreria locale si popola davvero
scandendo il filesystem e la riproduzione audio passa da `javafx-media`, quindi
l'interfaccia mostra dati reali e suona. Restano da fare playlist persistenti,
cue point, sincronizzazione online e test della GUI su display reale. Vedi
[ Stato del progetto ](#stato-del-progetto).

Snapshot della documentazione: commit `f7b976a` ("feat: libreria locale reale al
posto dei dati mock"), piu' le correzioni di compilazione di `HEAD`.

---

## Requisiti

| Componente | Versione |
|---|---|
| JDK | 21 |
| JavaFX | 21.0.1 |
| Maven | 3.6.3 (via `./mvnw`, scaricato al primo avvio) |
| Sistema | Desktop Linux/macOS/Windows con display (X11 o Wayland) |

Su Linux servono le librerie native GTK3 per JavaFX. Non è possibile eseguire
l'applicazione in modalità headless.

## Avvio rapido

```bash
cd CueMyMusic

./mvnw javafx:run        # compila e lancia l'app
```

Altri comandi utili:

```bash
./mvnw clean compile     # solo compilazione
./mvnw clean package     # produce un jar NON eseguibile (vedi note)
./mvnw clean             # rimuove target/
```

Il jar di `package` non è avviabile: il `pom.xml` non imposta un `Main-Class` nel
manifest e non include le dipendenze. Per la distribuzione serve `jpackage` o
`jlink`.

## Struttura del progetto

```
CueMyMusic/
├── pom.xml                       Build Maven (Java 21, JavaFX 21.0.1)
├── mvnw / mvnw.cmd               Maven Wrapper
├── Code_Explanation.md            Spiegazione annotata del codice (IT, obsoleta)
├── Code_Explanation_Update1.md   Delta del commit "UI Tweaks" (IT)
└── src/main/
    ├── java/com/cuemymusic/
    │   ├── App.java              Entry point + navigazione tra schermate
    │   ├── LoginController.java  Schermata di scelta della modalità d'accesso
    │   └── MainController.java   Schermata principale (libreria, coda, barra)
    └── src/test/java/com/cuemymusic/
        ├── MediaFilesTest.java     Regole di validazione e formattazione
        ├── LibraryServiceTest.java  Scansione reale, senza avviare il toolkit
        └── FxmlContractTest.java   Contratto FXML ↔ controller
    └── resources/com/cuemymusic/
        ├── fxml/login.fxml       Layout della schermata di login
        ├── fxml/main.fxml        Layout a 3 colonne della schermata principale
        ├── css/styles.css        Tema scuro globale (434 righe)
        └── fonts/                Comfortaa-Regular.ttf
```

Quattro dipendenze, tutte `org.openjfx` tranne JUnit 5: `javafx-controls`,
`javafx-fxml`, `javafx-media` (usata da `MediaPlayer`) e `junit-jupiter` in scope
`test`.

`./mvnw clean package` produce un jar **non avviabile**: il `pom.xml` non imposta
un `Main-Class` nel manifest e non include le dipendenze. Per la distribuzione
serve `jpackage` o `jlink`. `./mvnw javafx:run` è il modo supportato per avviare.

## Flusso utente

```
Avvio
  └─► Schermata di login / scelta modalità
        ├─ "Avvia in Locale"  ─┐
        └─ "Accedi" (user/pw) ─┴─► Schermata principale (a schermo intero)
                                      ├─ "Apri cartella…" → scansione in background
                                      ├─ 4 liste: Playlist, Libreria Locale,
                                      │            Ascoltati di Recente, Coda
                                      ├─ Doppio click su un brano → riproduzione
                                      ├─ Cerca per artista o titolo
                                      └─ Barra di riproduzione (titolo, artista,
                                         play/pausa, seek, volume, fullscreen)
```

Entrambe le opzioni di accesso portano alla stessa schermata: **non esiste
autenticazione** e la modalità scelta non viene memorizzata. Non c'è un pulsante per
tornare alla schermata di login.

## Stato del progetto

Cosa **funziona**:

- Avvio, caricamento FXML, navigazione login → main
- Tema scuro applicato globalmente e anche alle finestre di dialogo
- Gestione del fullscreen e ridimensionamento automatico della finestra iniziale
- Scansione della libreria su thread di background: cartelle nascoste saltate,
  link simbolici non seguiti, tetto di 20.000 brani e progress indicator
- Riproduzione audio con `MediaPlayer`: seek, volume, mute, fine brano, coda
- Filtro di ricerca su artista e titolo, con stato vuoto distinto
- Scorciatoie da tastiera (Spazio, F11, M, Ctrl+frecce, +/-)
- 35 test JUnit 5 sulla logica di scansione, sulle regole di validazione e sul
  contratto FXML ↔ controller

Cosa **non è ancora implementato**:

- Persistenza delle playlist e della libreria (ogni avvio richiede di riscegliere
  la cartella)
- Autenticazione e sincronizzazione con `CMM.cristianrenosto.party`
- Cue point e navigazione dedicata traccia precedente/successiva dalla coda
- Lettura dei tag ID3: artista e titolo sono dedotti dal filesystem
- Test della GUI su display reale (qui non è stato possibile: vedi §Verifica)

I due difetti segnalati in [`docs.md`](./docs.md) sono chiusi: il pulsante
play/pausa è ora abilitato non appena il brano ha una durata conosciuta, e i
pulsanti precedente/successiva hanno handler e scorciatoie da tastiera.

## Verifica

Cosa è stato provato davvero, su questo repository:

| Cosa | Come | Esito |
|---|---|---|
| Compilazione | `./mvnw clean compile` | ✅ |
| Test | `./mvnw clean test` | ✅ 35/35 |
| Packaging | `./mvnw clean package` | ✅ (jar non avviabile, come da nota) |
| Scansione libreria | `LibraryServiceTest`, 14 casi: cartelle nascoste, symlink interni ed esterni, file vuoti, ordinamento, metadati | ✅ |
| Contratto FXML ↔ controller | `FxmlContractTest`: `fx:id`, `onAction`, `styleClass` e risorse | ✅ |

Cosa **non** è stato possibile provare qui, e perche':

- **L'avvio della GUI.** JavaFX 21.0.1 su Linux è solo X11 (il `libglassgtk3.so`
  di questa build non contiene simboli Wayland) e in questa macchina gira solo un
  compositor Wayland, senza XWayland né `Xvfb`. Senza display non si puo' avviare
  il toolkit, quindi `javafx:run` non è stato eseguito.
- **La riproduzione audio.** `javafx-media` usa GStreamer di sistema, e qui
  mancano i demuxer (`wavparse`, `id3demux` non sono installati: il sistema ha
  `gst-plugins-base-libs` e `gst-plugins-bad-libs`, non `gst-plugins-good`). Il
  codice è corretto dal punto di vista delle API, ma non è stato possibile
  sentire un brano.

Entrambe le verifiche vanno rifatte su una macchina con display X11 (o XWayland) e
`gst-plugins-good` installato.

## Documentazione

| File | Contenuto |
|---|---|
| [`docs.md`](./docs.md) | Architettura, scene graph, design system, analisi del codice morto, gap e roadmap |
| `Code_Explanation.md` | Spiegazione annotata del codice — **obsoleta**: precede la libreria e la riproduzione |
| `Code_Explanation_Update1.md` | Delta del commit "UI Tweaks" — accurate ma incompleto |

## Licenza

Non presente nel repository.

## Further reading

External material covering the same ground. The cross-repo map, with the same
links for all seven projects, is in `~/Progetti/RESOURCES.md`.

CueMyMusic has the smallest footprint in this collection, so the honest mapping
is narrower than for the other six. Playback and library scanning are now wired
up, so the open questions are the cue-point model the app is named after, tag
parsing, and GUI testing on a real display.

### Build it from scratch

- [Awesome Web Audio](https://github.com/notthetup/awesome-webaudio) — the
  playback-state model behind `MediaPlayer`: buffering, end-of-media, and the
  cue-point semantics the README still lists as missing.
- [Crafting interpreters](http://www.craftinginterpreters.com/) *(Java)* —
  background for the screen-graph navigation in `App.java`, a small state
  machine over FXML-loaded scenes.

### Books

- [Google's Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Introduction to Programming Using Java](https://math.hws.edu/javanotes) — David J. Eck, with exercises

### Design system

The dark theme in `resources/com/cuemymusic/css/styles.css` is a design system
in the ordinary sense — tokens, spacing, a palette applied globally and to
dialogs — so:

- [Awesome Design Systems](https://github.com/klaufel/awesome-design-systems)
- [roadmap.sh/design-system](https://roadmap.sh/design-system)
- [Awesome Accessibility](https://github.com/brunopulis/awesome-a11y) — the
  dialogs and the fullscreen player bar have no keyboard-navigation or focus
  handling described in `docs.md`

### Reference

- [Awesome Java](https://github.com/akullpp/awesome-java)
- [roadmap.sh/java](https://roadmap.sh/java)
- [Project-based learning, Java section](https://github.com/practical-tutorials/project-based-learning#java)

### Not applicable

- **Build your own X** — a media player is not a compiler, a database, a
  browser or a network. That repo has no matching section.
- **System design primer** — a single-user desktop app with no server, no
  shared state and no partial failure.
