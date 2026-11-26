# liber-heroum-api · Runbook

> Procedure operative e risoluzione dei problemi di **liber-heroum-api**. Vale per l'ambiente di sviluppo in locale: la produzione non è ancora definita (vedi la sezione 5).

---

## 1. Panoramica operativa

### Componenti

Il backend gira fuori da Docker (`./mvnw spring-boot:run`), gli altri servizi nei container del `docker-compose`.

| Servizio | Cosa fa | Porta | Dipende da | Controllo di stato |
|---|---|---|---|---|
| **liber-heroum-api** | Backend REST | `8080` (API), `8081` (Actuator) | PostgreSQL, SeaweedFS, Keycloak (per le chiavi dei token) | `http://localhost:8081/actuator/health` |
| `postgres` | Database applicativo | `5432` | – | `docker compose ps` (healthy) |
| `postgres-keycloak` | Database di Keycloak | `5433` | – | `docker compose ps` (healthy) |
| `keycloak` | Login, token, ruoli | `8180` | `postgres-keycloak`, `mailpit` | `docker compose ps` (healthy) |
| `seaweedfs` | Storage S3 delle immagini | `8333` | – | nessuno |
| `mailpit` | Email di Keycloak | `8025` (web), `1025` (SMTP) | – | nessuno |
| `prometheus` | Raccoglie le metriche del backend ogni 15 secondi | `9090` (solo dal PC) | backend acceso sulla `8081` | `docker compose ps` (healthy) |
| `grafana` | Grafici delle metriche | `3000` (solo dal PC) | `prometheus` | nessuno |

Ordine di avvio: prima i container (aspettare che i servizi con controllo di stato siano `healthy`), poi il backend. Il backend all'avvio non contatta Keycloak: scarica le chiavi di firma alla prima richiesta con un token, e il client admin prende il suo token alla prima cancellazione di un account. Contatta invece lo storage per creare i bucket se mancano (se lo storage è spento scrive un avviso e parte lo stesso).

### Dove guardare i log

| Cosa | Come |
|---|---|
| Backend | Console in cui gira `./mvnw spring-boot:run`. Il package `it.liberheroum` logga a livello `DEBUG`. |
| Container | Dalla cartella `docker-compose`: `docker compose logs -f <servizio>` (`postgres`, `postgres-keycloak`, `keycloak`, `mailpit`, `seaweedfs`, `prometheus`, `grafana`). |

---

## 2. Procedure ordinarie

### Avviare e fermare

```bash
cd docker-compose
docker compose up -d        # avvia i servizi
docker compose ps           # stato dei servizi
docker compose down         # ferma i servizi, i dati restano
docker compose down -v      # ferma e cancella tutti i dati (reset totale)
```

Backend, dalla radice del progetto: `./mvnw spring-boot:run`.

### Controllare lo stato del backend

Actuator risponde solo sulla porta di gestione `8081`, senza il context path dell'API: `http://localhost:8081/actuator/health` risponde senza dettagli a tutti (con i gruppi `liveness` e `readiness`). Il dettaglio (database, disco) lo vede solo un utente con ruolo `admin`, con il suo token. Sulla porta `8080` Actuator non c'è più.

### Guardare le metriche

1. Avviare i container (`docker compose up -d`, compresi `prometheus` e `grafana`) e il backend.
2. Controllare che Prometheus legga il backend: `http://localhost:9090/targets`, il target `liber-heroum-api` deve essere `UP`.
3. Aprire Grafana su `http://localhost:3000` (utente `admin`, password `secret`, solo sviluppo), cartella **Liber Heroum**, dashboard **Liber Heroum API**: stato, richieste al secondo, tempi di risposta p95, errori, memoria, CPU, connessioni al database e thread.

Le metriche grezze si leggono anche a mano su `http://localhost:8081/actuator/prometheus`. La dashboard e la fonte dati stanno nel repository (`docker-compose/grafana`), Grafana le carica da solo all'avvio e non si modificano dall'interfaccia: si cambia il file JSON e Grafana lo rilegge entro pochi secondi. La storia delle metriche resta nel volume `prometheus-data` (15 giorni, il default di Prometheus).

### Reimportare il realm di Keycloak

Keycloak importa `docker-compose/keycloak/liber-heroum-dev-realm.json` solo alla prima creazione. Dopo una modifica al file:

```bash
cd docker-compose
docker compose rm -sf keycloak postgres-keycloak
docker volume rm docker-compose_postgres-keycloak-data
docker compose up -d
```

Il nome del volume è il nome della cartella del compose seguito da `_` e dal nome del volume: verificarlo con `docker volume ls`.

> **Attenzione**: gli utenti ricreati hanno un nuovo `sub`, quindi i dati dell'applicazione legati ai vecchi utenti non sono più raggiungibili. In sviluppo conviene azzerare anche il database (vedi sotto).

In alternativa si cambiano le impostazioni dalla console di Keycloak e si riportano a mano nel file JSON.

### Creare il client admin del backend

`DELETE /me` cancella l'utente su Keycloak con il client `liber-heroum-backend-admin`. Il file del realm lo contiene, ma l'importazione avviene solo alla prima creazione: in un realm già esistente va creato a mano. Console Keycloak (`http://localhost:8180`, utente `admin`, password `secret`) → realm `liber-heroum-dev`:

1. **Clients** → **Create client** → *Client type* `OpenID Connect`, *Client ID* `liber-heroum-backend-admin` → **Next**.
2. *Client authentication* **On**, *Authorization* **Off**; in *Authentication flow* lasciare acceso solo **Service accounts roles** (spegnere *Standard flow* e *Direct access grants*) → **Next** → **Save**.
3. Scheda **Credentials** → *Client Authenticator* `Client Id and Secret` → in *Client secret* il valore usato da `app.keycloak-admin.client-secret` (in sviluppo `secret`; si può rigenerare con **Regenerate**).
4. Scheda **Service accounts roles** → **Assign role** → filtrare per **Filter by clients** → `realm-management` `manage-users` → **Assign**.

Controllo: `curl -s -d grant_type=client_credentials -d client_id=liber-heroum-backend-admin -d client_secret=secret http://localhost:8180/realms/liber-heroum-dev/protocol/openid-connect/token` restituisce un `access_token`.

### Azzerare il database dell'applicazione

```bash
cd docker-compose
docker compose rm -sf postgres
docker volume rm docker-compose_postgres-data
docker compose up -d postgres
```

Al riavvio del backend Flyway ricrea lo schema. Le immagini nello storage non vengono toccate: per un reset coerente cancellare anche il volume `docker-compose_seaweedfs-data`, oppure usare `docker compose down -v`.

### Dare il ruolo `admin` a un utente

Console Keycloak (`http://localhost:8180`, utente `admin`, password `secret`) → realm `liber-heroum-dev` → **Users** → l'utente → **Role mapping** → **Assign role** → `admin`. L'utente deve fare un nuovo login: i ruoli sono scritti nel token.

### Cancellare un utente

`DELETE /me` cancella prima l'utente su Keycloak (client `liber-heroum-backend-admin`, con 3 tentativi in più a intervalli crescenti) e solo dopo i dati del backend. Il frontend non cancella più l'account.

- Si può richiamare: se l'utente non c'è più su Keycloak (404) si considera fatto, e se non c'è più nel database risponde 200 senza fare niente. Dopo un 502 o un 409 si richiama entro i 5 minuti dall'ultimo accesso con la password (`auth_time`), altrimenti il 401 chiede di rifare il login.
- L'account su Keycloak si può cancellare a mano dalla console: **Users** → l'utente → **Delete**.

### Leggere le email di Keycloak

In locale le email (verifica email, password dimenticata, cambio email) non partono davvero: si leggono su Mailpit, `http://localhost:8025`.

### Controllare lo storage

Con la CLI di AWS:

```bash
aws --endpoint-url http://localhost:8333 s3 ls
aws --endpoint-url http://localhost:8333 s3 ls s3://diary-documents
```

I bucket `diary-documents` (file degli utenti) e `feedback-documents` (screenshot dei feedback) li crea il backend all'avvio se mancano (`app.storage.create-buckets=true`, i nomi sono `app.storage.bucket` e `app.feedback.bucket`). Se lo storage è spento all'avvio il backend parte lo stesso e scrive un avviso nel log: riavviarlo, oppure creare i bucket a mano:

```bash
aws --endpoint-url http://localhost:8333 s3 mb s3://diary-documents
aws --endpoint-url http://localhost:8333 s3 mb s3://feedback-documents
```

### Pulizia dei file orfani

Ogni notte alle 3 (ora di Roma) il backend cancella dai bucket `diary-documents` e `feedback-documents` i file che non hanno una riga nella tabella `document` e sono più vecchi di 24 ore (`OrphanFileCleanup`, schedulato da `SchedulingConfig`). I file più giovani restano perché potrebbero essere upload in corso. Nel log c'è una riga per ogni file cancellato e un riepilogo finale («Orphan file cleanup finished: N files deleted»); se lo storage non risponde scrive un avviso e riprova la notte dopo.

Orario, fuso ed età minima si cambiano con `app.storage.orphan-cleanup-cron`, `app.storage.orphan-cleanup-zone` e `app.storage.orphan-min-age`; `app.storage.orphan-cleanup-enabled=false` spegne la pulizia. Con più istanze del backend la pulizia parte su una sola: le altre trovano il lock `orphanFileCleanup` nella tabella `shedlock` e scrivono nel log «Orphan file cleanup skipped: another instance holds the lock». Se un'istanza muore a metà, il lock scade da solo dopo un'ora.

### Leggere e cancellare i feedback

I feedback arrivano da `POST /feedback` (pubblico) e si gestiscono dalla Swagger UI con il token di un utente `admin` (in locale `admin1`, vedi «Dare il ruolo `admin` a un utente»):

- `GET /feedback` li elenca (filtri `type`, `status`, `email`, `createdFrom` e `createdTo` come date `yyyy-MM-dd`, i più recenti per primi); `GET /feedback/{id}` mostra il testo, le informazioni tecniche e gli screenshot con indirizzi temporanei di 5 minuti.
- `PATCH /feedback/{id}/read`, `/unread`, `/answered` e `/archive` cambiano lo stato.
- **Cancellazione su richiesta** (la Privacy la promette): cercare con `GET /feedback?email=...`, poi `DELETE /feedback/{id}`: cancella il feedback, i record degli screenshot e i file nel bucket `feedback-documents`.
- I feedback non si cancellano con `DELETE /me` perché non sono collegati all'account: chi chiede la cancellazione dei suoi dati va cercato per email.

### Ricostruire l'estensione di Keycloak

I sorgenti sono in `keycloak-extensions`, il jar compilato in `docker-compose/keycloak/providers/liber-heroum-kc-extension.jar`. Dopo averlo ricostruito riavviare Keycloak:

```bash
docker compose restart keycloak
```

---

## 3. Problemi noti

### Accesso e token

| Sintomo | Causa probabile | Rimedio |
|---|---|---|
| 401 su ogni richiesta | Il token non ha l'audience `liber-heroum-backend` (emesso prima del mapper o da un altro client), è scaduto (l'access token dura 5 minuti) oppure l'`issuer-uri` non risponde. | Fare un nuovo login. Decodificare il token e controllare `aud`, `iss`, `exp`. Controllare che `http://localhost:8180/realms/liber-heroum-dev` risponda e che il client `liber-heroum-api` abbia il mapper dell'audience. |
| 403 | Nel token manca il ruolo `user` o `admin` in `realm_access.roles`. | Assegnare il ruolo o inserire l'utente nel gruppo `users`, poi nuovo login. |
| `GET /me` risponde 404 | L'utente non è ancora stato creato nel database. | Chiamare `POST /me` dopo il login (lo fa il frontend). |
| `POST /me` risponde 400 «The token has no email address» | Il token non ha il claim `email`. | Controllare che l'utente abbia un'email in Keycloak. |
| `DELETE /me` risponde 502 «The account could not be deleted on the authentication server» | Keycloak non risponde o rifiuta la cancellazione anche dopo i tentativi (nel log del backend ci sono i `WARN` «Keycloak user ... not deleted» con lo stato HTTP): il client `liber-heroum-backend-admin` manca, ha un secret diverso da `app.keycloak-admin.client-secret` o non ha il ruolo `manage-users` (401 o 403), oppure `app.keycloak-admin.server-url` o `realm` sono sbagliati, o Keycloak è fermo. Nel backend non è stato cancellato niente. | Creare o correggere il client (vedi «Creare il client admin del backend»), controllare `server-url` e `realm`, poi ripetere la chiamata. |
| `DELETE /me` risponde 401 con `insufficient_user_authentication` | L'accesso è più vecchio di 5 minuti (`app.user.delete-max-auth-age`), oppure il token è stato preso con il password grant e non ha `auth_time`. | Rifare il login con la password dal frontend (Authorization Code con PKCE) e ripetere subito la chiamata. |

### Avvio

| Sintomo | Causa probabile | Rimedio |
|---|---|---|
| Il backend non parte con un errore di configurazione | Le proprietà sono validate e un valore sbagliato blocca l'avvio. Endpoint, chiavi e bucket dello storage sono obbligatori, `url-expiry` va da 1 secondo a 7 giorni, le dimensioni devono essere maggiori di zero, `delete-max-auth-age` almeno 1 secondo, `app.keycloak-admin.*` (`server-url`, `realm`, `client-id`, `client-secret`) obbligatorio. | Leggere il log: indica la proprietà. |
| Il build fallisce con un errore sulla versione di Java | È in uso un JDK diverso dal 25. | Controllare `java -version` e usare il JDK 25. |
| Connection refused sulla porta `5432` | `postgres` non è partito o non è `healthy`. | `docker compose ps`, poi `docker compose logs postgres`. |
| Il backend non parte con «Port 8081 was already in use» | Un altro processo usa la porta di Actuator (spesso un'altra istanza del backend). | Fermare l'altra istanza, oppure cambiare `management.server.port` (e il target in `docker-compose/prometheus/prometheus.yml`). |
| Su `http://localhost:9090/targets` il target `liber-heroum-api` è `DOWN` | Il backend è spento, oppure il firewall del PC blocca le connessioni dalla rete Docker verso la porta `8081` dell'host (Prometheus la raggiunge come `host.docker.internal`). | Avviare il backend; se è acceso, permettere al firewall le connessioni dalle reti Docker sulla `8081`. |
| Il backend non parte con errori di Flyway o di schema | Il volume del database esisteva già prima dello script `init-db-postgres.sql`, che gira solo alla creazione del volume: lo schema `dev` manca. | Azzerare il database (vedi sezione 2). |
| Il backend non parte con «Migration checksum mismatch» o «Detected applied migration not resolved locally» | Le migrazioni sono state riscritte o raggruppate (oggi sono solo `V1.0` e `V1.1`) e il database locale è stato creato con quelle vecchie. | Azzerare il database (vedi sezione 2). |

### Dati e API

| Sintomo | Causa probabile | Rimedio |
|---|---|---|
| 409 «The resource was modified elsewhere, reload it» | Modifica concorrente. Per le pagine la `version` inviata non è più quella del server. | Ricaricare i dati e ripetere la modifica. |
| 409 «... changed while it was being deleted, try again» | Difesa che non dovrebbe scattare: le cancellazioni di account, personaggi e diari bloccano la riga e cancellano in cascata anche quello che viene creato nel frattempo. | Ripetere la chiamata; se si ripete, controllare le chiavi esterne (devono essere `ON DELETE CASCADE`, vedi `SchemaIT`). |
| 400 «Invalid page content at ...» | Il contenuto della pagina non rientra in quello che accetta il validatore (peso massimo 50 KB, profondità 50). | Il messaggio indica il punto. Se l'editor ha una funzione nuova, va aggiunta anche in `DiaryPageContentValidator`. Caso noto: incollando HTML con `<ol type="x">` l'editor produce un valore che il backend rifiuta. |
| 400 «... contains characters that cannot be stored» | Il testo contiene il carattere NUL o metà di un'emoji. | Togliere i caratteri dal testo. |
| `POST /feedback` risponde 429 | Il limite di invii per indirizzo del reverse proxy (il backend non ha più un suo limite). | Aspettare, oppure controllare la regola del proxy. |
| `POST /feedback` risponde 201 ma il feedback non c'è | Il campo trappola `website` era compilato: il backend scrive nel log «Feedback discarded: the trap field was filled» e non salva niente. | Non mandare il campo `website`, o mandarlo vuoto. |
| `POST /feedback` risponde 400 «Too many files» o «Files too large» | Più di 3 immagini, oppure più di 5 MB in totale (`app.feedback.max-files`, `max-total-file-size`). | Ridurre gli screenshot. |

### Immagini e storage

| Sintomo | Causa probabile | Rimedio |
|---|---|---|
| 413 su un upload | Il file supera i 5 MB (`spring.servlet.multipart.max-file-size`, richiesta 6 MB). | Ridurre il file o alzare i limiti. |
| 400 su un upload | Il contenuto vero non coincide con il tipo dichiarato o con l'estensione. Ammessi png, jpeg e webp. | Caricare un'immagine valida con il tipo corretto. |
| Upload o lettura con errori dello storage | `seaweedfs` fermo, chiavi sbagliate o bucket mancante. | `docker compose ps`, controllare chiavi e bucket (vedi «Controllare lo storage»). |
| L'immagine non si apre dopo un po' | L'indirizzo temporaneo scade dopo 5 minuti. | Richiedere di nuovo il personaggio o il diario per avere un indirizzo nuovo. |
| File nel bucket senza riga nel database | La cancellazione dei file è best effort: se fallisce, il file resta fino alla pulizia notturna. | Aspettare la pulizia delle 3 (vedi «Pulizia dei file orfani»); i file più giovani di `app.storage.orphan-min-age` (24 ore) restano fino alla notte dopo. |

### Keycloak

| Sintomo | Causa probabile | Rimedio |
|---|---|---|
| Le modifiche al file del realm non hanno effetto | Il realm si importa solo alla prima creazione. | Vedi «Reimportare il realm di Keycloak». |
| L'importazione del realm fallisce | Un ruolo composito che include ruoli predefiniti come `uma_authorization`. | Dare il ruolo predefinito con un gruppo predefinito, non con un ruolo composito. |
| La pagina di login ha il tema standard | `liber-heroum-ui` non è nella cartella accanto a `liber-heroum-api` e il tema non viene montato. | Controllare che esista `../../liber-heroum-ui/keycloak-theme/themes` rispetto a `docker-compose`, poi `docker compose up -d`. |
| L'email di verifica non arriva | In locale le email vanno a Mailpit. | Aprire `http://localhost:8025`. |
| Il cambio email non si comporta come previsto | Manca il jar dell'estensione in `providers` o Keycloak non è stato riavviato dopo averlo ricostruito. Serve anche la funzione `update-email`, attiva nel compose. | Ricostruire il jar e `docker compose restart keycloak`. |

---

## 4. Backup e ripristino

Valido per lo sviluppo in locale. Database e bucket vanno salvati insieme, perché il database punta agli oggetti dello storage.

### Database applicativo

```bash
cd docker-compose
docker compose exec postgres pg_dump -U liber-heroum-api liber-heroum-api > backup.sql
```

Ripristino su un database vuoto (dopo averlo azzerato, vedi sezione 2):

```bash
docker compose exec -T postgres psql -U liber-heroum-api liber-heroum-api < backup.sql
```

### Immagini

Gli oggetti stanno nel volume `seaweedfs-data`. Per una copia leggibile:

```bash
aws --endpoint-url http://localhost:8333 s3 sync s3://diary-documents ./backup-immagini
aws --endpoint-url http://localhost:8333 s3 sync s3://feedback-documents ./backup-feedback
```

Per ripristinarle si inverte la sincronizzazione.

### Keycloak

Gli utenti stanno nel volume `postgres-keycloak-data`. In sviluppo non si salva: il realm e gli utenti di prova si ricreano dal file JSON.

---

## 5. Produzione

Ancora da definire. Prima di andare online:

- [ ] Dove gira il servizio e con quale budget
- [ ] Numeri attesi: utenti, spazio per utente, disponibilità richiesta e frequenza dei backup
- [ ] Invio reale delle email di Keycloak
- [ ] Protezione dai tentativi ripetuti di accesso e regole sulle password
- [ ] Realm separato di produzione, senza login diretto con password; valutare un client confidenziale
- [ ] CORS limitato all'origine esatta del frontend (`app.cors.allowed-origins`)
- [ ] Intestazioni `X-Content-Type-Options: nosniff` e `Cache-Control: no-store` sulle risposte con dati personali
- [ ] Chiavi e password fuori dal repository
- [ ] Antivirus sui file caricati
- [ ] Limite di invii per indirizzo su `POST /feedback` nel reverse proxy (es. 5 all'ora): il backend non ne ha uno suo
- [ ] Porta di Actuator (`8081`) mai pubblicata su internet né inoltrata dal reverse proxy: Prometheus la legge dalla rete interna. Con Docker non va messa in `ports:` (Docker scavalca il firewall `ufw`); valutare `management.server.address` sull'interfaccia interna. Password vera di Grafana e Grafana dietro login.
- [ ] Avvisi sulle metriche (errori 5xx, tempi di risposta) da definire
- [ ] Limite alla dimensione della richiesta a livello di server, oggi il peso si controlla dopo la lettura del corpo
- [ ] Feedback: `server.forward-headers-strategy` dietro il reverse proxy (altrimenti il limite per indirizzo vale per tutti), bucket `feedback-documents` nei backup, durata di conservazione dei feedback, notifica al titolare (oggi il backend non manda email)
