# MC Display World (Java MVP)

You asked for Java, so this starter is now a **Java 17 + Maven** app.

It gives you a multiplayer-friendly base where one person creates a room, shares the URL, and everyone can access uploaded Minecraft model/texture assets.

## Features

- Create a shareable room.
- Upload files from mod/texture-pack pipelines (`.json`, `.png`, `.mcmeta`, etc.).
- List and download room assets by URL.
- In-memory room state with file persistence under `uploads/`.

## Run

```bash
mvn exec:java
```

Then open `http://localhost:3000`.

## Test

```bash
mvn test
```

## API

- `POST /api/rooms`
- `GET /api/rooms/:roomId`
- `PUT /api/rooms/:roomId/assets/:fileName`
- `GET /uploads/:roomId/:fileName`

## Next steps for your full game idea

1. Parse mod JARs / resource packs and auto-index model+texture relationships.
2. Add realtime multiplayer sync via WebSockets.
3. Render model previews with a proper Minecraft-compatible viewer.
4. Add auth + room permissions so only allowed friends can upload/edit.
