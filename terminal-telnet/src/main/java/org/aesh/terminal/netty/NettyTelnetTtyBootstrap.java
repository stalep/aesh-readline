package org.aesh.terminal.netty;

import org.aesh.terminal.Connection;
import org.aesh.terminal.TelnetTtyConnection;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NettyTelnetTtyBootstrap {

  private final NettyTelnetBootstrap telnet;
  private boolean outBinary;
  private boolean inBinary;
  private Charset charset = StandardCharsets.UTF_8;

  public NettyTelnetTtyBootstrap() {
    this.telnet = new NettyTelnetBootstrap();
  }

  public String getHost() {
    return telnet.getHost();
  }

  public NettyTelnetTtyBootstrap setHost(String host) {
    telnet.setHost(host);
    return this;
  }

  public int getPort() {
    return telnet.getPort();
  }

  public NettyTelnetTtyBootstrap setPort(int port) {
    telnet.setPort(port);
    return this;
  }

  public boolean isOutBinary() {
    return outBinary;
  }

  /**
   * Enable or disable the TELNET BINARY option on output.
   *
   * @param outBinary true to require the client to receive binary
   * @return this object
   */
  public NettyTelnetTtyBootstrap setOutBinary(boolean outBinary) {
    this.outBinary = outBinary;
    return this;
  }

  public boolean isInBinary() {
    return inBinary;
  }

  /**
   * Enable or disable the TELNET BINARY option on input.
   *
   * @param inBinary true to require the client to emit binary
   * @return this object
   */
  public NettyTelnetTtyBootstrap setInBinary(boolean inBinary) {
    this.inBinary = inBinary;
    return this;
  }

  public Charset getCharset() {
    return charset;
  }

  public void setCharset(Charset charset) {
    this.charset = charset;
  }

  public CompletableFuture<?> start(Consumer<Connection> factory) {
    CompletableFuture<?> fut = new CompletableFuture<>();
    start(factory, startedHandler(fut));
    return fut;
  }

  public CompletableFuture<?> stop() {
    CompletableFuture<?> fut = new CompletableFuture<>();
    stop(stoppedHandler(fut));
    return fut;
  }

  public void start(Consumer<Connection> factory, Consumer<Throwable> doneHandler) {
    telnet.start(() -> new TelnetTtyConnection(inBinary, outBinary, charset), doneHandler);
  }

  public void stop(Consumer<Throwable> doneHandler) {
    telnet.stop(doneHandler);
  }

  public static Consumer<Throwable> startedHandler(CompletableFuture<?> fut) {
    return err -> {
      if (err == null) {
        fut.complete(null);
      } else {
        fut.completeExceptionally(err);
      }
    };
  }

  public static Consumer<Throwable> stoppedHandler(CompletableFuture<?> fut) {
    return err -> {
      fut.complete(null);
    };
  }
}
