package com.mcdisplayworld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class McDisplayServerTest {
  private McDisplayServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void sanitizeNameReplacesUnsafeChars() {
    assertEquals(".._weird_file_.json", McDisplayServer.sanitizeName("../weird file?.json"));
  }

  @Test
  void createRoomAndUploadAsset() throws IOException, InterruptedException {
    server = new McDisplayServer(3201);
    server.start();

    HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> roomResp = client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:3201/api/rooms"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(201, roomResp.statusCode());
    String roomId = roomResp.body().split("\"id\":\"")[1].split("\"")[0];
    assertTrue(roomId.length() == 8);

    HttpResponse<String> uploadResp = client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:3201/api/rooms/" + roomId + "/assets/test.json"))
            .PUT(HttpRequest.BodyPublishers.ofString("{\"parent\":\"item/generated\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(201, uploadResp.statusCode());

    HttpResponse<String> stateResp = client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:3201/api/rooms/" + roomId))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, stateResp.statusCode());
    assertTrue(stateResp.body().contains("test.json"));
  }
}
