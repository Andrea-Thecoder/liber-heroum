# Changelog

Le modifiche rilevanti di **Liber Heroum** (backend e frontend) vengono annotate in questo file. Il formato segue [Keep a Changelog](https://keepachangelog.com/it-IT/1.1.0/).

## [Non rilasciato]

### Aggiunto

- Metriche del backend: endpoint `/actuator/prometheus`, Prometheus e Grafana nel `docker-compose` con la dashboard **Liber Heroum API** (richieste, tempi di risposta p95, errori, memoria, CPU, connessioni al database).

### Modificato

- Actuator del backend spostato sulla porta interna `8081`: l'health ora è `http://localhost:8081/actuator/health` (prima `http://localhost:8080/api/v1/liber-heroum-api/actuator/health`); `/actuator/metrics` non è più esposto, gli stessi numeri sono in `/actuator/prometheus`.

- Il prodotto si chiama **Liber Heroum** ("Le cronache dei tuoi eroi"): rinominati testi e titoli dell'app, pagine di login ed email di Keycloak (sigillo "LH"), nomi visualizzati nel realm, titolo dell'API e nome del file dell'esportazione dei dati. Rinominati anche i nomi tecnici: cartelle `liber-heroum-api` e `liber-heroum-ui`, package `it.liberheroum`, realm `liber-heroum-dev`, client e audience di Keycloak, database, percorso dell'API (`/api/v1/liber-heroum-api`), tema ed estensione di Keycloak, cookie del tema (`lh-theme`) e archivio delle bozze nel browser (il tema scelto e le bozze non salvate si perdono una volta).
