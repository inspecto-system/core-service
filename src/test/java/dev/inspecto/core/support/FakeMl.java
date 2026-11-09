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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stands for ml-service in tests (an external boundary, CODE_QUALITY.md §4): answers the
 * classification with a configured body, status or delay and records every call.
 */
public final class FakeMl implements AutoCloseable {

  private final HttpServer server;
  private final List<Call> calls = new CopyOnWriteArrayList<>();
  private final AtomicReference<String> answer = new AtomicReference<>("");
  private final AtomicInteger status = new AtomicInteger(200);
  private final AtomicLong delayMillis = new AtomicLong();

  private FakeMl(HttpServer server) {
    this.server = server;
  }

  /** Starts the fake on a free local port. */
  public static FakeMl start() {
    try {
      HttpServer server =
          HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      FakeMl fake = new FakeMl(server);
      server.createContext("/internal/v1/classify", fake::handle);
      server.start();
      return fake;
    } catch (IOException e) {
      throw new IllegalStateException("cannot start fake ml-service", e);
    }
  }

  /** Base URL to configure as {@code inspecto.ml.url}. */
  public String url() {
    return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
  }

  /** Answers {@code 200} with the predictions, for example {@code {"code":"CRACK",...}} items. */
  public void answer(String modelVersion, String predictions) {
    answer.set("{\"modelVersion\":\"" + modelVersion + "\",\"predictions\":[" + predictions + "]}");
    status.set(200);
    delayMillis.set(0);
  }

  /** Answers every following call with the status and no body. */
  public void failWith(int code) {
    status.set(code);
  }

  /** Answers only after the delay. */
  public void delay(long millis) {
    delayMillis.set(millis);
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
    try {
      Thread.sleep(delayMillis.get());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    int code = status.get();
    FakeMedia.respond(exchange, code, code == 200 ? answer.get() : "");
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
