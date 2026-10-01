# CueMyMusic

> Progetto in mostra su **[cristianrenosto.party/projects](https://cristianrenosto.party/projects)**

> Player musicale desktop in JavaFX, orientato al concetto di *cue point* e alle
> playlist da DJ (stile "walk it / talk it").

**Stato: prototipo di interfaccia.** L'applicazione si avvia, disegna le schermate e
cambia vista correttamente, ma **non riproduce audio e non legge alcun dato**: la
libreria, le playlist, la cronologia e la coda restano vuote. Vedi
[ Stato del progetto ](#stato-del-progetto).

Snapshot della documentazione: commit `c8ee37b` ("UI Tweaks: auto-sizing, bigger
proportions, removed mock data").

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
    └── resources/com/cuemymusic/
        ├── fxml/login.fxml       Layout della schermata di login
        ├── fxml/main.fxml        Layout a 3 colonne della schermata principale
        ├── css/styles.css        Tema scuro globale (185 righe)
        └── fonts/                Comfortaa-Regular.ttf (non caricato)
```

Solo tre dipendenze, tutte `org.openjfx`: `javafx-controls`, `javafx-fxml` e
`javafx-media`. **Nota:** `javafx-media` è attualmente inutilizzata — non c'è nessun
`MediaPlayer` nel codice.

## Flusso utente

```
Avvio
  └─► Schermata di login / scelta modalità
        ├─ "Avvia in Locale"  ─┐
        └─ "Accedi" (user/pw) ─┴─► Schermata principale (a schermo intero)
                                      ├─ 4 liste: Playlist, Libreria Locale,
                                      │            Ascoltati di Recente, Coda
                                      ├─ Barra di riproduzione (titolo, artista,
                                      │   play/pausa, seek, fullscreen)
                                      └─ Copertina (segnaposto grigio)
```

Entrambe le opzioni di accesso portano alla stessa schermata: **non esiste
autenticazione** e la modalità scelta non viene memorizzata. Non c'è un pulsante per
tornare alla schermata di login.

## Stato del progetto

Cosa **funziona**:

- Avvio, caricamento FXML, navigazione login → main
- Tema scuro applicato globalmente e anche alle finestre di dialogo
- Gestione del fullscreen e ridimensionamento automatico della finestra iniziale

Cosa **non è ancora implementato**:

- Riproduzione audio (nessun uso di `javafx-media`)
- Scansione della libreria locale su filesystem
- Persistenza delle playlist
- Autenticazione e sincronizzazione con `CMM.cristianrenosto.party`
- Cue point, coda di riproduzione e navigazione traccia precedente/successiva
- Test automatici (nessuna directory `src/test`)

Ci sono inoltre due dettagli noti, analizzati nel dettaglio in
[`docs.md`](./docs.md): il pulsante play/pausa è **sempre** irraggiungibile perché il
flag `hasMedia` non viene mai impostato a `true`, e i pulsanti traccia
precedente/successiva non hanno handler associato.

## Documentazione

| File | Contenuto |
|---|---|
| [`docs.md`](./docs.md) | Architettura, scene graph, design system, analisi del codice morto, gap e roadmap |
| `Code_Explanation.md` | Spiegazione annotata del codice — **parzialmente obsoleta** |
| `Code_Explanation_Update1.md` | Delta del commit "UI Tweaks" — accurate ma incompleto |

## Licenza

Non presente nel repository.

## Further reading

External material covering the same ground. The cross-repo map, with the same
links for all seven projects, is in `~/Progetti/RESOURCES.md`.

CueMyMusic has the smallest footprint in this collection, so the honest mapping
is narrower than for the other six. The one substantial gap in the project — no
audio playback, with `javafx-media` declared but unused — is a Web Audio
problem, and that is where the material concentrates.

### Build it from scratch

- [Awesome Web Audio](https://github.com/notthetup/awesome-webaudio) — the
  closest thing to a syllabus for what `javafx-media` would need: playback
  state, buffering, and the cue-point model the README lists as missing.
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
