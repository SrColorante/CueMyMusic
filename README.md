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
