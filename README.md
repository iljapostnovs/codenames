# Scala & UI5 Codenames game
This is a project of Codenames game implementation.

## UI5
Frontend is based on OpenUI5 framework.
In order to run frontend go to frontend folder, install node packages.
```
npm install
npm install --global @ui5/cli
ui5 serve
```

## Scala
Backend is built using Scala sbt.
Web service is an entry point for the frontend.
HTTP and WebSocket service is implemented using scala http4s library.
In order to launch the service go to codenames folder and execute:
```
sbt run
```