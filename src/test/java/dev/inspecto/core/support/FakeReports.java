package dev.inspecto.core.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.inspecto.core.support.FakeMedia.Call;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands for report-service in tests (an external boundary, CODE_QUALITY.md §4): accepts the
 * internal bulk deletion and records every call with the claims of its service token.
 */
public final class FakeReports implements AutoCloseable {

  private final HttpServer server;
  private final List<Call> calls = new CopyOnWriteArrayList<>();
  private final AtomicInteger failWith = new AtomicInteger();

  private FakeReports(HttpServer server) {
    this.server = server;
  }

  /** Starts the fake on a free local port. */
  public static FakeReports start() {
    try {
      HttpServer server =
          HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      FakeReports fake = new FakeReports(server);
      server.createContext("/internal/v1/reports", fake::handle);
      server.start();
      return fake;
    } catch (IOException e) {
      throw new IllegalStateException("cannot start fake report-service", e);
    }
  }

  /** Base URL to configure as {@code inspecto.report.url}. */
  public String url() {
    return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
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
    calls.add(
        new Call(
            exchange.getRequestMethod(),
            exchange.getRequestURI().toString(),
            FakeMedia.claims(exchange.getRequestHeaders().getFirst("Authorization"))));
    int status = "DELETE".equals(exchange.getRequestMethod()) ? 204 : 405;
    if (failWith.get() != 0) {
      status = failWith.get();
    }
    FakeMedia.respond(exchange, status, "");
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
