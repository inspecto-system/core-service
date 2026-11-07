package dev.inspecto.core.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands for media-service in tests (an external boundary, CODE_QUALITY.md §4): serves the internal
 * photo endpoints from memory and records every call with the claims of its service token.
 */
public final class FakeMedia implements AutoCloseable {

  private static final String PHOTOS = "/internal/v1/photos";

  private final HttpServer server;
  private final Map<UUID, String> photos = new ConcurrentHashMap<>();
  private final List<Call> calls = new CopyOnWriteArrayList<>();
  private final AtomicInteger failWith = new AtomicInteger();

  /** A recorded request: method, path with query, and the decoded payload of the bearer token. */
  public record Call(String method, String path, String tokenClaims) {}

  private FakeMedia(HttpServer server) {
    this.server = server;
  }

  /** Starts the fake on a free local port. */
  public static FakeMedia start() {
    try {
      HttpServer server =
          HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      FakeMedia fake = new FakeMedia(server);
      server.createContext(PHOTOS, fake::handle);
      server.start();
      return fake;
    } catch (IOException e) {
      throw new IllegalStateException("cannot start fake media-service", e);
    }
  }

  /** Base URL to configure as {@code inspecto.media.url}. */
  public String url() {
    return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
  }

  /** Makes a photo known, as if the owner had uploaded it for the property. */
  public UUID upload(UUID ownerId, UUID propertyId) {
    UUID id = UUID.randomUUID();
    photos.put(
        id,
        "{\"id\":\"%s\",\"ownerId\":\"%s\",\"propertyId\":\"%s\",\"status\":\"UPLOADED\"}"
            .formatted(id, ownerId, propertyId));
    return id;
  }

  /** Answers every following call with the status, or normally again with 0. */
  public void failWith(int status) {
    failWith.set(status);
  }

  /** Calls received so far. */
  public List<Call> calls() {
    return List.copyOf(calls);
  }

  /** Forgets the recorded calls. */
  public void clearCalls() {
    calls.clear();
  }

  private void handle(HttpExchange exchange) throws IOException {
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().toString();
    calls.add(
        new Call(method, path, claims(exchange.getRequestHeaders().getFirst("Authorization"))));
    if (failWith.get() != 0) {
      respond(exchange, failWith.get(), "");
      return;
    }
    String[] parts = exchange.getRequestURI().getPath().substring(PHOTOS.length()).split("/");
    UUID id = parts.length > 1 ? UUID.fromString(parts[1]) : null;
    switch (method) {
      case "GET" ->
          respond(exchange, photos.containsKey(id) ? 200 : 404, photos.getOrDefault(id, ""));
      case "POST" -> respond(exchange, photos.containsKey(id) ? 204 : 404, "");
      default -> {
        if (id != null) {
          photos.remove(id);
        }
        respond(exchange, 204, "");
      }
    }
  }

  static String claims(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return "";
    }
    String[] jwt = authorization.substring("Bearer ".length()).split("\\.");
    return jwt.length < 2
        ? ""
        : new String(Base64.getUrlDecoder().decode(jwt[1]), StandardCharsets.UTF_8);
  }

  static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    }
    exchange.close();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
