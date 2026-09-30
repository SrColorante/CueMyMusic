# CueMyMusic — Documentazione tecnica

Documento di riferimento approfondito per **CueMyMusic**, player musicale desktop
JavaFX.

> **Snapshot:** commit `c8ee37b` ("UI Tweaks: auto-sizing, bigger proportions,
> removed mock data"), 3 commit complessivi su `master`.
> Per una panoramica rapida vedi [`README.md`](./README.md).

---

## 1. Sommario

1. [Contesto e stato del progetto](#1-contesto-e-stato-del-progetto)
2. [Stack tecnologico](#2-stack-tecnologico)
3. [Architettura](#3-architettura)
4. [Inventario dei file](#4-inventario-dei-file)
5. [Navigazione tra schermate](#5-navigazione-tra-schermate)
6. [Scene graph delle interfacce](#6-scene-graph-delle-interfacce)
7. [Design system](#7-design-system)
8. [Modello di dominio](#8-modello-di-dominio)
9. [Analisi del codice morto](#9-analisi-del-codice-morto)
10. [Build e run](#10-build-e-run)
11. [Valutazione della documentazione esistente](#11-valutazione-della-documentazione-esistente)
12. [Gap funzionali e roadmap](#12-gap-funzionali-e-roadmap)
13. [Appendice](#13-appendice)

---

## 1. Contesto e stato del progetto

CueMyMusic nasce come player musicale per desktop con un'impostazione da DJ: il
nome e la commit iniziale (`Setup JavaFX CueMyMusic app`) indicano l'idea di gestire
**cue point** — punti di riferimento all'interno di un brano, tipici dei software per
performance live — insieme a playlist e coda di riproduzione.

La realtà del codice è però molto più acerba: si tratta di uno **shell di
interfaccia**. Le schermate sono disegnate correttamente e la navigazione funziona,
ma non esiste alcuna logica applicativa:

- nessuna classe di dominio, nessuna interfaccia, nessun servizio;
- nessun accesso a rete, filesystem, database o file di configurazione;
- nessuna riproduzione audio, nonostante `javafx-media` sia nelle dipendenze;
- nessun test.

Il nome del prodotto promette un concetto — il **cue** — che **non compare mai nel
codice**: non esistono `Cue`, `CuePoint`, `hotcue` o identificatori analoghi in alcun
file `.java` o `.fxml`. È quindi impossibile dedurre dal repository cosa l'autore
intenda per "cue".

### 1.1 Criterio di verità

Ogni affermazione in questo documento è stata verificata direttamente sul sorgente
alla commit indicata. Dove il codice contraddice la documentazione preesistente, la
documentazione preesistente è indicata come obsoleta (§11).

---

## 2. Stack tecnologico

Definito interamente in `pom.xml` (57 righe).

| Proprietà | Valore |
|---|---|
| GAV | `com.cuemymusic:CueMyMusic:1.0-SNAPSHOT` (packaging `jar`) |
| `maven.compiler.source` / `target` | `21` |
| `project.build.sourceEncoding` | `UTF-8` |
| `javafx.version` | `21.0.1` |
| `mainClass` | `com.cuemymusic.App` |

### 2.1 Dipendenze

Solo tre artefatti, tutti del gruppo `org.openjfx`:

| Artefatto | Versione | Utilizzato? | Impiego reale |
|---|---|---|---|
| `javafx-controls` | 21.0.1 | ✅ | `Application`, `Scene`, `ListView`, `Button`, `Label`, `Slider`, `Alert`, `TextField`, `PasswordField` |
| `javafx-fxml` | 21.0.1 | ✅ | `FXMLLoader` e iniezione `@FXML` |
| `javafx-media` | 21.0.1 | ❌ | **Nessuno.** Zero riferimenti a `MediaPlayer`, `Media` o `AudioClip` in `src/` |

La presenza di `javafx-media` è un indicatore di intenzione: il playback audio era
previsto ma non è mai stato implementato.

### 2.2 Plugin

| Plugin | Versione | Configurazione |
|---|---|---|
| `maven-compiler-plugin` | 3.11.0 | `source`/`target` 21 (duplicati rispetto alle proprietà) |
| `org.openjfx:javafx-maven-plugin` | 0.0.8 | `mainClass = com.cuemymusic.App` — abilita `mvn javafx:run` |

### 2.3 Assenze rilevanti

Nessun `module-info.java` (l'app gira sul classpath, non come modulo JPMS), nessuna
dipendenza di test (JUnit/TestNG assenti), nessun plugin shade/assembly/exec, nessun
`<repositories>` dichiarato, nessuna integrazione CI (`.github/` non esiste).

---

## 3. Architettura

### 3.1 Modello

Architettura **FXML + Controller** nella forma più semplice possibile:

- ogni schermata è un file `.fxml` che dichiara il proprio controller via
  `fx:controller`;
- i controller ricevono i nodi tramite campi annotati `@FXML`;
- **non esiste un layer di servizio, di dominio o di persistenza**;
- la navigazione è affidata a metodi `static` su `App`.

Tutto il codice di produzione sta in **tre classi** (157 righe totali), tutte in un unico
package piatto `com.cuemymusic`. Non ci sono sotto-package.

```
                    ┌──────────────┐
                    │     App      │  Application + statics di navigazione
                    │  (50 righe)  │
                    └──────┬───────┘
          ┌────────────────┴────────────────┐
          │                                 │
   loadFXML("login")                App.setRoot("main")
          │                                 │
   ┌──────▼─────────┐             ┌─────────▼────────┐
   │ login.fxml     │             │ main.fxml        │
   │ 35 righe       │             │ 104 righe        │
   ├────────────────┤             ├──────────────────┤
   │LoginController │────────────▶│ MainController   │
   │ 41 righe       │ setRoot     │ 66 righe         │
   └────────────────┘             └──────────────────┘
          │                                 │
          └──────── styles.css (185) ────────┘
```

### 3.2 Stato dell'applicazione

Lo stato vive in **campi `static` mutabili** su `App`:

```java
private static Scene scene;
private static Stage primaryStage;
```

Non è iniettato, non è incapsulato e non è testabile. I controller non ricevono
dipendenze: per ottenere la `Stage`, `MainController` aggira `App` e usa
`fullscreenButton.getScene().getWindow()`.

### 3.3 Flusso di avvio

1. `javafx:run` invoca `com.cuemymusic.App.main` → `launch()`.
2. `start(Stage)` salva la `Stage` in `primaryStage`.
3. Le due righe di caricamento font sono **commentate** (si veda §7.4).
4. `new Scene(loadFXML("login"))` — la `Scene` non ha dimensioni fisse, si adatta al
   contenuto.
5. `styles.css` viene aggiunto agli stylesheet della `Scene`.
6. `stage.setScene` + `setTitle("CueMyMusic")` + `sizeToScene()` + `show()`.

---

## 4. Inventario dei file

### 4.1 Codice sorgente

| File | Righe | Responsabilità |
|---|---|---|
| `src/main/java/com/cuemymusic/App.java` | 50 | Sottoclasse di `Application`; costruisce la `Scene` di login, inietta il CSS globale, espone `setRoot()` / `setFullscreen()` come helper statici, e `loadFXML()` privato |
| `src/main/java/com/cuemymusic/LoginController.java` | 41 | Handler della schermata di accesso: modalità locale e sincronizzazione online (entrambe mock) |
| `src/main/java/com/cuemymusic/MainController.java` | 66 | Controller della schermata principale: 4 `ListView`, etichette della barra, `togglePlayPause` (irraggiungibile), `toggleFullscreen`, helper `showError` |
| **Totale codice Java** | **157** | |

### 4.2 Risorse

| File | Righe | Contenuto |
|---|---|---|
| `src/main/resources/com/cuemymusic/fxml/login.fxml` | 35 | Scena di scelta modalità: `VBox` radice con titolo, sottotitolo e due card affiancate |
| `src/main/resources/com/cuemymusic/fxml/main.fxml` | 104 | Scena principale a 3 colonne: libreria/playlist, cronologia + barra, coda |
| `src/main/resources/com/cuemymusic/css/styles.css` | 185 | Tema scuro globale; applicato anche alle `Alert` |
| `src/main/resources/com/cuemymusic/fonts/Comfortaa-Regular.ttf` | binario | Font incluso ma **mai caricato** (cfr. §7.4) |
| **Totale risorse testuali** | **324** | |

### 4.3 Build

| File | Note |
|---|---|
| `pom.xml` | 57 righe |
| `mvnw` / `mvnw.cmd` | Maven Wrapper (POSIX `sh` / Windows) |
| `.mvn/wrapper/maven-wrapper.properties` | `distributionUrl` → Maven **3.6.3**; `wrapperUrl` → wrapper 0.5.6 |
| `.mvn/wrapper/maven-wrapper.jar` | Committato: non serve il bootstrap via rete per il jar, ma la distribuzione Maven 3.6.3 viene scaricata in `~/.m2/wrapper` al primo avvio |
| `.gitignore` | Standard + `target/`, `log/` |

### 4.4 Documentazione preesistente

| File | Righe | Lingua |
|---|---|---|
| `Code_Explanation.md` | 178 | Italiano |
| `Code_Explanation_Update1.md` | 96 | Italiano |

Nessun `README.md` o documentazione di build esisteva prima di questo intervento.

---

## 5. Navigazione tra schermate

Esiste **un solo passaggio**, e avviene da entrambe le azioni di login:

```java
@FXML
private void handleLocalAccess() throws IOException {
    System.out.println("Starting in local mode...");
    App.setRoot("main");
    App.setFullscreen(true);
}
```

I due helper centrali sono:

```java
public static void setRoot(String fxml) throws IOException {
    scene.setRoot(loadFXML(fxml));
}

public static void setFullscreen(boolean fullscreen) {
    primaryStage.setFullScreen(fullscreen);
}
```

### 5.1 Caratteristiche del modello di navigazione

- **Una sola `Scene`**, il cui `root` viene sostituito. Non esistono scene separate né
  una pila di navigazione.
- **Nessun ritorno**: `main.fxml` non ha pulsante di logout o "indietro". L'unica via
  d'uscita dal fullscreen è il pulsante `⛶`.
- **`handleLocalAccess` e `handleOnlineSync` sono identiche** nel comportamento: la
  modalità scelta non viene registrata da nessuna parte.
- **Nessun `Platform.runLater`**: le transizioni sono sincrone.
- Lo `StatusLabel` della login scrive `"Connecting to " + endpoint + "..."` e subito
  dopo diventa invisibile, perché `setRoot("main")` sostituisce l'intero albero.

`LoginController` contiene anche commenti che dichiarano esplicitamente il mock:

```java
// Mock endpoint connection setup
// In the future this will authenticate. For now we just mock and proceed.
```

---

## 6. Scene graph delle interfacce

Entrambi i file FXML usano `xmlns="http://javafx.com/fxml/21"` e hanno
`styleClass="root-pane"` sulla radice.

### 6.1 `login.fxml` → `LoginController`

```
VBox [root-pane]  (alignment=CENTER, spacing=20, padding 40/40/40/40)
├── Label  "CueMyMusic"                              .title-label
├── Label  "Scegli la modalità di accesso:"          .subtitle-label
└── HBox (spacing 40, alignment=CENTER)
    ├── VBox .login-card            [ Accesso Locale ]
    │   ├── Label .card-title
    │   ├── Label .card-subtitle
    │   └── Button "Avvia in Locale"  → #handleLocalAccess
    └── VBox .login-card fx:id=onlineLoginBox   [ Sincronizzazione Online ]
        ├── Label .card-title
        ├── Label .card-subtitle   ("Endpoint: CMM.cristianrenosto.party")
        ├── TextField     fx:id=usernameField   .input-field
        ├── PasswordField fx:id=passwordField   .input-field
        ├── Button "Accedi" → #handleOnlineSync  .action-button .primary-button
        └── Label fx:id=statusLabel              .status-label
```

### 6.2 `main.fxml` → `MainController`

Radice `GridPane` (padding 15, `hgap`/`vgap` 15) con **3 colonne** e **1 riga**:

| Colonna | Larghezza | Contenuto |
|---|---|---|
| 0 | 25% | `GridPane` con 2 righe (50% / 50%) |
| 1 | 50% | `GridPane` con 2 righe (75% / 25%) |
| 2 | 25% | `VBox` singola |

```
GridPane [root-pane]
│
├── [col 0] GridPane (righe 50% / 50%)
│   ├── VBox .panel   "Playlists"       + ListView fx:id=playlistsList    .minimal-list
│   └── VBox .panel   "Libreria Locale" + ListView fx:id=localFilesList   .minimal-list
│
├── [col 1] GridPane (righe 75% / 25%)
│   ├── VBox .panel "Ascoltati di Recente" + ListView fx:id=recentSongsList .minimal-list
│   └── HBox .waybar (CENTER_LEFT, spacing 15, padding 10/15/10/15)
│       ├── Rectangle 60x60, arc 10        .cover-art   ← segnaposto grigio
│       ├── VBox (HBox.hgrow=ALWAYS)
│       │   ├── Label fx:id=currentSongLabel    .song-title
│       │   ├── Label fx:id=currentArtistLabel  .song-artist
│       │   └── HBox (CENTER, spacing 10)
│       │       ├── Button "⏮"                              .control-button   [nessun handler]
│       │       ├── Button fx:id=playPauseButton "▶"         .control-button .play-button
│       │       │                                          → #togglePlayPause
│       │       ├── Button "⏭"                              .control-button   [nessun handler]
│       │       └── Slider fx:id=progressBar    .progress-bar   (HBox.hgrow=ALWAYS)
│       └── Button fx:id=fullscreenButton "⛶"  .control-button → #toggleFullscreen
│
└── [col 2] VBox .panel "Coda di Riproduzione" + ListView fx:id=queueList .minimal-list .queue-list
```

---

## 7. Design system

Tutto il tema è in un unico file, `styles.css` (185 righe), caricato come stylesheet
della `Scene` in `App.start()` e **riapplicato manualmente** a ogni `Alert`:

```java
alert.getDialogPane().getStylesheets().add(
    getClass().getResource("/com/cuemymusic/css/styles.css").toExternalForm());
alert.getDialogPane().getStyleClass().add("root-pane");
```

### 7.1 Classi di stile principali

| Classe | Applicata a | Ruolo |
|---|---|---|
| `root-pane` | radici di entrambe le scene, `DialogPane` | Sfondo scuro globale |
| `title-label` | titolo "CueMyMusic" | 48px |
| `subtitle-label` | "Scegli la modalità…" | 22px |
| `login-card` | le due card della login | `pref-width` 450, `min-width` 450, padding 40, raggio 20 |
| `card-title` / `card-subtitle` | titoli delle card | 28px / 16px |
| `input-field` | `TextField`, `PasswordField` | raggio 12, padding 10/15, 18px |
| `action-button` `.primary-button` | "Accedi" | raggio 12, padding 15/30, 20px |
| `status-label` | etichetta di stato | 14px |
| `panel` | i 4 pannelli della main | raggio 20, padding 25 |
| `minimal-list` `.list-cell` | le 4 liste | padding 12, raggio 12 |
| `.queue-list .list-cell:filled:selected` | cella selezionata della coda | Sfondo bianco — inteso come "brano in riproduzione" |
| `waybar` | barra di riproduzione | raggio 25 |
| `cover-art` | `Rectangle` copertina | `#444444` |
| `song-title` / `song-artist` | titolo e artista correnti | 24px / 18px |
| `control-button` / `play-button` | pulsanti di trasporto | 28px / 32px, padding 10/20, raggio 30 |
| `progress-bar .track` / `.thumb` | slider della barra | raggio 8 / 15 — **selettori non funzionanti, vedi §9.4** |

Dimensione base: **18px** (`.status-label` a 14px è l'unica eccezione, al di sotto
della base).

### 7.2 Leggibilità del layout

- `sizeToScene()` viene chiamato **una sola volta**, all'avvio, sulla scena di login.
- Dopo `setRoot("main")` la `Stage` mantiene le dimensioni derivate dal login; il
  `GridPane` principale si affida solo a vincoli percentuali, senza
  `minWidth`/`minHeight`.
- Larghezza minima della login ≈ `2 × 450` (card) `+ 40` (spacing) `+ 80` (padding) =
  **≈ 1020 px**. Su schermi più piccoli la finestra di login può superare il display.
- L'app entra in fullscreen forzato da entrambe le azioni di login.

### 7.3 Palette

Il tema è una variante scura con accenti bianchi. I colori sono definiti inline
nelle regole; non esistono custom properties CSS né token riutilizzabili.

### 7.4 Font: un problema aperto

`styles.css:4` dichiara la famiglia:

```css
-fx-font-family: "Comfortaa", "Segoe UI", sans-serif;
```

ma il caricamento del font in `App.java` è **commentato**:

```java
// Load custom fonts (fallback to system fonts)
// Font.loadFont(getClass().getResourceAsStream("/com/cuemymusic/fonts/Comfortaa-Regular.ttf"), 14);
// Font.loadFont(getClass().getResourceAsStream("/com/cuemymusic/fonts/Comfortaa-Bold.ttf"), 14);
```

Conseguenze:

1. Il file `Comfortaa-Regular.ttf` è **incluso nella repository ma mai caricato**.
2. La seconda riga commentata riferisce `Comfortaa-Bold.ttf`, un file **che non esiste**
   nella cartella `fonts/`.
3. Su una macchina senza Comfortaa installato a livello di sistema, l'intera UI
   ripiega silenziosamente su `Segoe UI`/sans-serif.

---

## 8. Modello di dominio

**Non esiste.** Non ci sono classi entità, POJO, interfacce, né alcun rapporto tra
elementi. L'intero "modello" è costituito da campi dei controller:

```java
@FXML private ListView<String> localFilesList;
@FXML private ListView<String> playlistsList;
@FXML private ListView<String> recentSongsList;
@FXML private ListView<String> queueList;

@FXML private Label currentSongLabel;
@FXML private Label currentArtistLabel;
@FXML private Slider progressBar;
@FXML private Button playPauseButton;
@FXML private Button fullscreenButton;

private boolean isPlaying = false;
private boolean hasMedia = false;
```

In `LoginController`: `onlineLoginBox`, `usernameField`, `passwordField`, `statusLabel`
— iniettati, e utilizzati solo quest'ultimo.

### 8.1 Concetti che la UI implica ma non modella

| Concetto | Dove appare | Modello esistente |
|---|---|---|
| Playlist / set | `playlistsList` | Nessuno. Le stringhe mock del prototipo originale erano nomi ("Workout Mix", "Chill Vibes", "Favorites") |
| Libreria locale | `localFilesList` | Nessuno. Le stringhe mock erano **cartelle** (`"Folder: /home/devCri/Music"`), non brani |
| Brano | solo come `String` in una cella | Formato mock `"Artista - Titolo"`. A schermo: titolo + artista. Nessun id, percorso o durata |
| Ascolti recenti | `recentSongsList` | Nessuno. Nessun ordinamento temporale |
| Coda | `queueList` | Nessuno. Il CSS evidenzia la cella selezionata come "in riproduzione", ma nulla la imposta |
| **Cue** | — | **Assente dal codice**, nonostante il nome del prodotto |
| Copertina | `Rectangle` grigio | Segnaposto; nessun `ImageView` |

Le quattro `ListView<String>` sono slot UI indipendenti: non esiste un riferimento da
playlist a brani, né dalla coda alla libreria.

### 8.2 Sorgenti dati

**Nessuna.** Una ricerca su tutto `src/` non trova alcun riferimento a `java.net`,
`HttpClient`, `Socket`, `FileInputStream`/`FileOutputStream`, `Files`, `Preferences`,
`DirectoryStream` o `MediaPlayer`.

L'unico I/O è il caricamento di risorse dal classpath. L'app non legge né scrive nulla
su disco, non ha database, non ha file di configurazione e non effettua chiamate di
rete. L'unico output persistente è un `System.out.println` di debug in
`LoginController`.

All'avvio le quattro liste sono vuote e `initialize()` imposta solo due etichette:

```java
currentSongLabel.setText("Nessun brano in riproduzione");
currentArtistLabel.setText("-");
```

---

## 9. Analisi del codice morto

Sezione di rilievo perché questi punti influenzano la percezione dell'app da parte
dell'utente: diversi elementi dell'interfaccia *sembrano* funzionanti ma non lo fanno.

### 9.1 `hasMedia` non viene mai impostato a `true`

```java
private boolean hasMedia = false; // flag to track if a song is loaded

@FXML
private void togglePlayPause() {
    if (!hasMedia) {
        showError("Nessun file multimediale selezionato", "Seleziona prima un brano dalla libreria per avviare la riproduzione.");
        return;
    }
    isPlaying = !isPlaying;
    playPauseButton.setText(isPlaying ? "⏸" : "▶");
}
```

`hasMedia` è dichiarato a `false` e **letto** a riga 39, ma non è mai assegnato a
`true` in tutto il repository. Di conseguenza il ramo di `return` a riga 41 è
**permanentemente preso**: premere play mostra sempre un errore.

Restano morti di conseguenza: il flag `isPlaying`, lo scambio del simbolo
`⏸`/`▶` sul pulsante, e l'intero blocco di stile `.play-button`.

### 9.2 `javafx-media` inutilizzata

Nessun riferimento a `MediaPlayer`, `Media` o `AudioClip`. La "riproduzione" è una
semplice inversione di un booleano.

### 9.3 Pulsanti di trasporto senza handler

In `main.fxml:85,87` i pulsanti `⏮` e `⏭` non hanno né `fx:id` né `onAction`. Non
hanno alcuna funzionalità e non sono raggiungibili da codice.

### 9.4 Selettori CSS del progress bar inerti

`styles.css:175-185` definisce `.progress-bar .track` e `.progress-bar .thumb`, ma il
nodo è un `Slider`, la cui classe di stile è `slider`, non `progress-bar`. I selettori
corretti sarebbero `.slider .track` e `.slider .thumb`. Inoltre lo `Slider` non ha
`min`/`max` impostati né listener.

### 9.5 Evidenziazione della coda irraggiungibile

La regola `.queue-list .list-cell:filled:selected` (sfondo bianco) è un retaggio
dell'istanza mock, dove `initialize()` chiamava
`queueList.getSelectionModel().select(0)`. Con la coda vuota la regola non può mai
attivarsi.

### 9.6 Credenziali e dipendenze iniettate ma inutilizzate

In `LoginController` i campi `usernameField`, `passwordField` e `onlineLoginBox` sono
iniettati e **mai letti**: le credenziali vengono raccolte e scartate. Anche
`import javafx.scene.control.Button;` è inutilizzato.

In `App.java` sono inutilizzati `javafx.scene.text.Font` (citato solo nei commenti) e
`javafx.stage.StageStyle`.

### 9.7 Copertina

`main.fxml:77` è un `Rectangle` 60×60 con raggio 10, commentato
`<!-- Cover Art Placeholder -->` e colorato `#444444`. Non c'è alcun `ImageView`.

### 9.8 `target/` obsoleto

La directory `target/` è un build **precedente** al commit `c8ee37b`. Contiene ancora
il foglio di stile vecchio (`.title-label: 32px`, `.login-card` con `padding:30` e
`pref-width:300`). Poiché `.gitignore` esclude `target/`, questa obsolescenza non è
visibile da `git status`. Da un `clean` in poi scompare.

---

## 10. Build e run

Il progetto usa **solo il Maven Wrapper** (Maven 3.6.3).

```bash
cd /home/devCri/CueMyMusic

./mvnw -v              # bootstrap: scarica Maven 3.6.3 in ~/.m2/wrapper al primo avvio
./mvnw javafx:run      # compila e avvia l'app   <-- comando principale
./mvnw clean compile   # solo compilazione in target/classes
./mvnw clean package   # jar (NON eseguibile)
./mvnw clean           # rimuove target/
```

Su Windows: `mvnw.cmd javafx:run`.
Variabile utile: `MVNW_VERBOSE=true` per il log del wrapper.
`.mvn/jvm.config` viene letto dallo script ma **non esiste**.

### 10.1 Prerequisiti

- **JDK 21** (`source`/`target` = 21, JavaFX 21.0.1)
- **Una sessione grafica** (X11 o Wayland): JavaFX non si avvia headless e il `pom.xml`
  non prevede TestFX/Monocle
- Su Linux, le librerie native GTK3 per JavaFX (requisito standard OpenJFX, non
  dichiarato né verificato nel repository)
- I nativi sono risolti automaticamente dai classificatori di piattaforma degli
  artefatti `org.openjfx` 21.0.1

### 10.2 Il jar non è distribuibile

`./mvnw clean package` produce un jar che **non si avvia**: il `pom.xml` non imposta
`Main-Class` nel manifest e le dipendenze non sono incluse. Per una distribuzione
usabile servono `jpackage` o `jlink`.

### 10.3 Test

**Non esistono.** Nessuna directory `src/test`, nessuna dipendenza test-scoped, nessuna
configurazione surefire, nessuna CI. `./mvnw test` ha successo con **0 test**.

---

## 11. Valutazione della documentazione esistente

I due file `Code_Explanation*.md` sono utili ma **non affidabili** senza correzioni.
Sono in italiano e commentano il codice riga per riga, includendo il sorgente
integrale dei tre file Java.

### 11.1 `Code_Explanation.md` (178 righe)

Copre bene `App.java` (concetto di `Application`, `Scene`/`Stage` statici, helper di
navigazione) e la struttura di `LoginController`.

**Obsoleto:**

| Dichiarazione | Realtà attuale |
|---|---|
| `scene = new Scene(loadFXML("login"), 800, 600);` | `App.java:25` è `new Scene(loadFXML("login"))` + `sizeToScene()` a riga 30 |
| Blocco `MainController` con dati mock e `getItems().addAll(...)` | Il mock è stato **rimosso**; oggi ci sono `hasMedia`, `showError()`, import `Alert`/`AlertType`, e l'import `HBox` che il doc elenca è **stato rimosso** |
| `import javafx.scene.layout.HBox;` in `MainController` | Non più presente |
| Una sola riga di caricamento font, "per far usare al sistema il **Comfortaa nativo**" | Oggi ci sono **due** righe commentate (Regular e Bold); il commento nel codice dice `fallback to system fonts`, e il doc non può verificare se Comfortaa sia installato a livello OS |

L'unica sezione ancora logicamente corretta è quella su `LoginController` (logica
invariata; cambiano solo i commenti, in inglese nel codice e in italiano nel doc).

### 11.2 `Code_Explanation_Update1.md` (96 righe)

Descrive il commit `c8ee37b` in tre sezioni: dimensionamento finestra in `App.java`,
scale-up globale in `styles.css`, rimozione dei dati mock in `MainController.java`.

**Verificato accurato** per `App.java` e `MainController.java` — ogni affermazione
corrisponde al sorgente corrente.

**Due imprecisioni nella sezione CSS:**

1. Dichiara che la dimensione base fosse "14px o 16px" prima della modifica. La
   versione precedente **non aveva alcun `-fx-font-size`** nella regola `*`.
2. È **incompleta**: lo stesso commit ha modificato anche `.panel` (raggio 15→20,
   padding 15→25), `.card-title` 20→28, `.card-subtitle` 12→16, `.input-field`,
   `.action-button`, `.status-label`, `.minimal-list .list-cell`, `.waybar`,
   `.play-button` e i raggi di `.progress-bar .track`/`.thumb`.

### 11.3 Cosa nessuno dei due file copre

Contenuti di `login.fxml` e `main.fxml`; il layout a 3 colonne; la struttura delle
risorse; le istruzioni di build e avvio; e — soprattutto — il fatto che `hasMedia` non
viene mai impostato a `true`, il che rende **definitivamente irraggiungibile** il
comportamento descritto in §3 di `Update1`.

---

## 12. Gap funzionali e roadmap

### 12.1 Non implementato

| Area | Stato |
|---|---|
| Scansione libreria su filesystem | Assente. Manca qualsiasi I/O |
| Riproduzione audio | Assente, nonostante `javafx-media` sia in classpath |
| Cue point | **Assente**: il concept che dà il nome al prodotto non esiste nel codice |
| Persistenza playlist | Assente. Nessun formato dati definito |
| Autenticazione | Assente. Le credenziali vengono scartate |
| Sincronizzazione remota | Assente. `CMM.cristianrenosto.party` è una stringa hardcoded in `LoginController.java:29`, senza client, protocollo o configurazione |
| Coda e brano corrente | Slot UI vuoti, nessuna logica |
| Traccia precedente/successiva | Pulsanti senza handler |
| Controllo volume | Assente |
| Copertina | Segnaposto `Rectangle` |
| Modello di dominio | Assente. Solo `String` nelle liste |
| Test | Assenti |
| CI | Assente |
| Licenza | Assente |

### 12.2 Debito tecnico

1. **Flag mai impostati** — `hasMedia` blocca l'unico flusso attivo (§9.1).
2. **Selettori CSS errati** — `.progress-bar` invece di `.slider` (§9.4).
3. **Font non caricato** — `Comfortaa` richiede installazione di sistema; il file
   bundled è inutilizzato e il riferimento al Bold punta a un file assente (§7.4).
4. **Stato globale statico** — scena e stage come `static` mutabili su `App`, non
   testabili e non incapsulati.
5. **`sizeToScene()` chiamato una sola volta** — la finestra principale non viene
   dimensionata correttamente al cambio scena.
6. **Nessuna via d'uscita** dalla schermata principale verso il login.
7. **Dipendenza inutilizzata** — `javafx-media` in `pom.xml`.
8. **Import inutilizzati** — `Font`, `StageStyle` in `App.java`; `Button` in
   `LoginController.java`.

### 12.3 Direzioni possibili

L'ordine è quello di dipendenze tecniche, non di valore:

1. Modellare il dominio (brano, playlist, coda) e lo strato di persistenza.
2. Implementare la scansione della libreria locale e popolare le liste.
3. Collegare `javafx-media` a un `MediaPlayer` reale e rimuovere il blocco su
   `hasMedia`.
4. Correggere i selettori CSS e il caricamento del font.
5. Decidere cosa sia un **cue** e implementarlo — è la ragione d'essere del progetto.
6. Introdurre test (TestFX per la UI, JUnit per la logica).

---

## 13. Appendice

### 13.1 Riferimenti di codice

| Simbolo | File:righe |
|---|---|
| `App.start` | `App.java:19-32` |
| `App.setRoot` | `App.java:34-36` |
| `App.setFullscreen` | `App.java:38-40` |
| `App.loadFXML` | `App.java:42-45` |
| `MainController.hasMedia` | `MainController.java:26` |
| `MainController.initialize` | `MainController.java:28-35` |
| `MainController.togglePlayPause` | `MainController.java:37-46` |
| `MainController.toggleFullscreen` | `MainController.java:48-52` |
| `MainController.showError` | `MainController.java:54-65` |
| Endpoint remoto hardcoded | `LoginController.java:29` |
| Selettori CSS inerti | `styles.css:175-185` |
| Dichiarazione font-family | `styles.css:4` |

### 13.2 Glossario

| Termine | Significato in questo progetto |
|---|---|
| **FXML** | Formato XML che descrive un albero di nodi JavaFX, caricato a runtime da `FXMLLoader` |
| **Controller** | Classe Java che gestisce gli eventi di una scena FXML; i campi `@FXML` ricevono i nodi corrispondenti per `fx:id` |
| **Cue point** | Punto di riferimento segnato all'interno di un brano, usato nei software per DJ per navigare velocemente. **Non implementato** in questo progetto |
| **Style class** | Classe CSS applicata a un nodo JavaFX, usata come selettore (`.nome-classe`) |
| **`sizeToScene()`** | Metodo di `Stage` che ridimensiona la finestra sul contenuto della scena |
| **Fullscreen** | Modalità a schermo intero, in cui il `WindowManager` nasconde decorazioni e barra delle applicazioni |

### 13.3 Cosa **non** è determinabile dal codice

- Il significato inteso di **"cue"**: nessun file lo definisce.
- Se `CMM.cristianrenosto.party` sia un servizio reale: non c'è codice client, né
  protocollo, né configurazione — solo la stringa hardcoded.
- Se "set" e "playlist" siano concetti distinti: nell'app compare solo la parola
  "Playlists".
- Se `Comfortaa` sia installato a livello di sistema sulla macchina di sviluppo: non è
  deducibile dal repository.

### 13.4 Metodo

Verifica diretta del sorgente alla commit `c8ee37b`: lettura di `pom.xml`, dei tre
file Java, di entrambi gli FXML e di `styles.css`; conteggio righe; ricerca di
riferimenti a I/O, rete e `javafx.scene.media`; confronto con lo stato del repository
git; confronto con `target/classes` per ricostruire la versione precedente del CSS.
