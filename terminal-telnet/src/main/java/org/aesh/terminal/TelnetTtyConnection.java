/*
 * Copyright 2015 Julien Viet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.aesh.terminal;


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.aesh.io.Decoder;
import org.aesh.io.Encoder;
import org.aesh.terminal.netty.TelnetChannelHandler;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class TelnetTtyConnection extends TelnetHandler implements Connection {

    private final boolean inBinary;
    private final boolean outBinary;
    private boolean receivingBinary;
    private boolean sendingBinary;
    private boolean accepted;
    private Size size;
    private String terminalType;
    private Consumer<Size> sizeHandler;
    private Consumer<String> termHandler;
    private Consumer<Void> closeHandler;
    protected TelnetConnection conn;
    private final Charset charset;
    private final EventDecoder eventDecoder = new EventDecoder(3, 26, 4);
    //private final ReadBuffer readBuffer = new ReadBuffer(this::execute);
    //private final Decoder decoder = new Decoder(512, TelnetCharset.INSTANCE, readBuffer);
    private final Decoder decoder = new Decoder(512, TelnetCharset.INSTANCE, eventDecoder);
    private final Encoder encoder = new Encoder(StandardCharsets.US_ASCII, data -> conn.write(data));
    private final Consumer<int[]> stdout = new TtyOutputMode(encoder);
    //private final Consumer<Connection> handler;
    private long lastAccessedTime = System.currentTimeMillis();

    //public TelnetTtyConnection(boolean inBinary, boolean outBinary, Charset charset, Consumer<Connection> handler) {
    public TelnetTtyConnection(boolean inBinary, boolean outBinary, Charset charset) {
        this.charset = charset;
        this.inBinary = inBinary;
        this.outBinary = outBinary;
        //this.handler = handler;
    }

    //@Override
    public long lastAccessedTime() {
        return lastAccessedTime;
    }

    @Override
    public String terminalType() {
        return terminalType;
    }

    //@Override
    public void execute(Runnable task) {
        conn.execute(task);
    }

    //@Override
    public void schedule(Runnable task, long delay, TimeUnit unit) {
        conn.schedule(task, delay, unit);
    }

    @Override
    public Charset inputCharset() {
        return inBinary ? charset : StandardCharsets.US_ASCII;
    }

    @Override
    public Charset outputCharset() {
        return outBinary ? charset : StandardCharsets.US_ASCII;
    }

    @Override
    protected void onSendBinary(boolean binary) {
        sendingBinary = binary;
        if (binary) {
            encoder.setCharset(charset);
        }
        checkAccept();
    }

    @Override
    protected void onReceiveBinary(boolean binary) {
        receivingBinary = binary;
        if (binary) {
            decoder.setCharset(charset);
        }
        checkAccept();
    }

    @Override
    protected void onData(byte[] data) {
        lastAccessedTime = System.currentTimeMillis();
        decoder.write(data);
    }

    @Override
    protected void onOpen(TelnetConnection conn) {
        this.conn = conn;

        // Kludge mode
        conn.writeWillOption(Option.ECHO);
        conn.writeWillOption(Option.SGA);

        //
        if (inBinary) {
            conn.writeDoOption(Option.BINARY);
        }
        if (outBinary) {
            conn.writeWillOption(Option.BINARY);
        }

        // Window size
        conn.writeDoOption(Option.NAWS);

        // Get some info about user
        conn.writeDoOption(Option.TERMINAL_TYPE);

        //
        checkAccept();
    }

    private void checkAccept() {
        if (!accepted) {
            if (!outBinary | (outBinary && sendingBinary)) {
                if (!inBinary | (inBinary && receivingBinary)) {
                    accepted = true;
                    //readBuffer.setReadHandler(eventDecoder);
                    //handler.accept(this);
                }
            }
        }
    }

    @Override
    protected void onTerminalType(String terminalType) {
        this.terminalType = terminalType;
        if (termHandler != null) {
            termHandler.accept(terminalType);
        }
    }

    @Override
    public Size size() {
        return size;
    }

    @Override
    protected void onSize(int width, int height) {
        this.size = new Size(width, height);
        if (sizeHandler != null) {
            sizeHandler.accept(size);
        }
    }

    @Override
    public Consumer<Size> getSizeHandler() {
        return sizeHandler;
    }

    @Override
    public void setSizeHandler(Consumer<Size> handler) {
        this.sizeHandler = handler;
    }

    //@Override
    public Consumer<String> getTerminalTypeHandler() {
        return termHandler;
    }

    //@Override
    public void setTerminalTypeHandler(Consumer<String> handler) {
        termHandler = handler;
        if (handler != null && terminalType != null) {
            handler.accept(terminalType);
        }
    }

    @Override
    public Consumer<Signal> getSignalHandler() {
        return eventDecoder.getSignalHandler();
    }

    @Override
    public void setSignalHandler(Consumer<Signal> handler) {
        eventDecoder.setSignalHandler(handler);
    }

    @Override
    public Consumer<int[]> getStdinHandler() {
        return eventDecoder.getInputHandler();
    }

    @Override
    public void setStdinHandler(Consumer<int[]> handler) {
        eventDecoder.setInputHandler(handler);
    }

    @Override
    public Consumer<int[]> stdoutHandler() {
        return stdout;
    }

    @Override
    public void setCloseHandler(Consumer<Void> closeHandler) {
        this.closeHandler = closeHandler;
    }

    @Override
    public Consumer<Void> getCloseHandler() {
        return closeHandler;
    }

    @Override
    protected void onClose() {
        if (closeHandler != null) {
            closeHandler.accept(null);
        }
    }

    @Override
    public void close() {
        conn.close();
    }

    @Override
    public void openBlocking() {
        NioEventLoopGroup group = new NioEventLoopGroup();
        DefaultChannelGroup channelGroup = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);

        TelnetHandler telnetHandler = this;
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(group)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 100)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        channelGroup.add(ch);
                        ChannelPipeline p = ch.pipeline();
                        TelnetChannelHandler handler = new TelnetChannelHandler(telnetHandler);
                        p.addLast(handler);
                    }
                });

        bootstrap.bind("localhost", 4000).addListener(fut -> {
            if (fut.isSuccess()) {
                System.out.println("bootstrap is success");
                //doneHandler.accept(null);
            } else {
                System.out.println("bootstrap failed...");
                //doneHandler.accept(fut.cause());
            }
        });
    }

    @Override
    public void openNonBlocking() {

    }

    @Override
    public void stopReading() {

    }

    @Override
    public void suspend() {

    }

    @Override
    public boolean suspended() {
        return false;
    }

    @Override
    public void awake() {

    }

    @Override
    public boolean put(Capability capability, Object... params) {
        return false;
    }
}
