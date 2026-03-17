package com.mcdisplayworld;

public class Main {
  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3000"));
    McDisplayServer server = new McDisplayServer(port);
    server.start();
    System.out.println("mcdisplayworld listening on http://localhost:" + port);
  }
}
