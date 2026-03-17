package com.mcdisplayworld;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class McDisplayServer {
  private static final Path UPLOAD_DIR = Path.of("uploads").toAbsolutePath().normalize();
  private static final String INDEX_HTML = loadResource("/public/index.html");

  private final HttpServer server;
  private final Map<String, Room> rooms = new ConcurrentHashMap<>();

  public McDisplayServer(int port) throws IOException {
    Files.createDirectories(UPLOAD_DIR);
    server = HttpServer.create(new InetSocketAddress(port), 0);
    server.setExecutor(Executors.newCachedThreadPool());
    server.createContext("/", new Router());
  }

  public void start() {
    server.start();
  }

  public void stop() {
    server.stop(0);
  }

  private static String loadResource(String path) {
    try (InputStream in = McDisplayServer.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing resource " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to load resource " + path, ex);
    }
  }

  public static String sanitizeName(String name) {
    String base = (name == null || name.isBlank()) ? "file.bin" : name;
    String safe = base.replaceAll("[^a-zA-Z0-9._-]", "_");
    return safe.length() > 120 ? safe.substring(0, 120) : safe;
  }

  private Room getOrCreateRoom(String id) {
    return rooms.computeIfAbsent(id, Room::new);
  }

  private class Router implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      try {
        String method = exchange.getRequestMethod();
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();

        if ("POST".equals(method) && "/api/rooms".equals(path)) {
          createRoom(exchange);
          return;
        }

        if (path.startsWith("/api/rooms/")) {
          String[] parts = path.split("/");
          if (parts.length >= 4 && "GET".equals(method) && parts.length == 4) {
            getRoom(exchange, parts[3]);
            return;
          }
          if (parts.length >= 6 && "PUT".equals(method) && "assets".equals(parts[4])) {
            uploadAsset(exchange, parts[3], parts[5]);
            return;
          }
        }

        if ("GET".equals(method) && path.startsWith("/uploads/")) {
          serveUpload(exchange, path);
          return;
        }

        if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
          send(exchange, 200, "text/html; charset=utf-8", INDEX_HTML.getBytes(StandardCharsets.UTF_8));
          return;
        }

        if (path.startsWith("/api/")) {
          sendJson(exchange, 404, "{\"error\":\"Not Found\"}");
          return;
        }

        send(exchange, 404, "text/plain; charset=utf-8", "Not Found".getBytes(StandardCharsets.UTF_8));
      } catch (Exception ex) {
        sendJson(exchange, 500, "{\"error\":\"Server error\",\"details\":\"" + escape(ex.getMessage()) + "\"}");
      }
    }

    private void createRoom(HttpExchange ex) throws IOException {
      String roomId = UUID.randomUUID().toString().substring(0, 8);
      Room room = getOrCreateRoom(roomId);
      sendJson(ex, 201, room.toJson());
    }

    private void getRoom(HttpExchange ex, String roomId) throws IOException {
      Room room = rooms.get(roomId);
      if (room == null) {
        sendJson(ex, 404, "{\"error\":\"Room not found\"}");
        return;
      }
      sendJson(ex, 200, room.toJson());
    }

    private void uploadAsset(HttpExchange ex, String roomId, String rawFileName) throws IOException {
      String fileName = sanitizeName(rawFileName);
      byte[] body = ex.getRequestBody().readAllBytes();
      if (body.length == 0) {
        sendJson(ex, 400, "{\"error\":\"Empty upload\"}");
        return;
      }

      Room room = getOrCreateRoom(roomId);
      Path roomDir = UPLOAD_DIR.resolve(roomId).normalize();
      Files.createDirectories(roomDir);
      Path filePath = roomDir.resolve(fileName).normalize();

      if (!filePath.startsWith(UPLOAD_DIR)) {
        sendJson(ex, 400, "{\"error\":\"Invalid path\"}");
        return;
      }

      Files.copy(new java.io.ByteArrayInputStream(body), filePath, StandardCopyOption.REPLACE_EXISTING);

      Asset asset = new Asset(fileName, body.length, "uploads/" + roomId + "/" + fileName);
      room.upsert(asset);
      sendJson(ex, 201, asset.toJson());
    }

    private void serveUpload(HttpExchange ex, String requestPath) throws IOException {
      String relative = requestPath.substring(1);
      Path target = Path.of(relative).toAbsolutePath().normalize();
      if (!target.startsWith(UPLOAD_DIR) || !Files.exists(target) || Files.isDirectory(target)) {
        sendJson(ex, 404, "{\"error\":\"Not Found\"}");
        return;
      }

      byte[] content = Files.readAllBytes(target);
      String type = Files.probeContentType(target);
      send(ex, 200, type == null ? "application/octet-stream" : type, content);
    }
  }

  private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
    send(ex, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
  }

  private static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
    Headers headers = ex.getResponseHeaders();
    headers.set("Content-Type", contentType);
    ex.sendResponseHeaders(status, body.length);
    try (OutputStream out = ex.getResponseBody()) {
      out.write(body);
    }
  }

  private static String escape(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static class Room {
    private final String id;
    private final String createdAt;
    private final List<Asset> assets = new ArrayList<>();

    private Room(String id) {
      this.id = id;
      this.createdAt = Instant.now().toString();
    }

    private synchronized void upsert(Asset asset) {
      assets.removeIf(existing -> existing.name.equals(asset.name));
      assets.add(asset);
    }

    private synchronized String toJson() {
      StringBuilder sb = new StringBuilder();
      sb.append("{\"id\":\"").append(escape(id)).append("\",");
      sb.append("\"createdAt\":\"").append(escape(createdAt)).append("\",");
      sb.append("\"assets\":[");
      for (int i = 0; i < assets.size(); i++) {
        if (i > 0) {
          sb.append(',');
        }
        sb.append(assets.get(i).toJson());
      }
      sb.append("]}");
      return sb.toString();
    }
  }

  private static class Asset {
    private final String name;
    private final int size;
    private final String path;
    private final String uploadedAt;

    private Asset(String name, int size, String path) {
      this.name = name;
      this.size = size;
      this.path = path;
      this.uploadedAt = Instant.now().toString();
    }

    private String toJson() {
      return "{\"name\":\"" + escape(name) + "\",\"size\":" + size
          + ",\"path\":\"" + escape(path) + "\",\"uploadedAt\":\"" + escape(uploadedAt) + "\"}";
    }
  }
}
