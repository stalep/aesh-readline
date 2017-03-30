/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
import org.aesh.readline.Readline;
import org.aesh.readline.ReadlineBuilder;
import org.aesh.readline.tty.terminal.TerminalConnection;
import org.aesh.terminal.Connection;
import org.aesh.terminal.TelnetHandler;
import org.aesh.terminal.TelnetTtyConnection;
import org.aesh.terminal.netty.TelnetChannelHandler;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

/**
 * A very simple example where we use the default values and create a simple
 * terminal application that reads the input and returns it.
 * Typing "exit" or ctrl-c/ctrl-d will exit the program.
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class SimpleTelnetExample {

    public static void main(String... args) throws IOException {
        TelnetTtyConnection connection = new TelnetTtyConnection(false, false, StandardCharsets.UTF_8);
        openBlocking(connection);
        //we're setting up readline to read when connection receives any input
        //note that this needs to be done after every time Readline.readline returns
        read(connection, ReadlineBuilder.builder().enableHistory(false).build(), "[aesh@rules]$ ");
        //lets open the connection to the terminal using this thread
        //connection.openBlocking();

        /* nonBlocking
        connection.openNonBlocking();
        Readline readline = ReadlineBuilder.builder().enableHistory(false).build();
        String prompt = "[aesh@rules]$ ";
        while(true) {
            String input = reading(connection, readline, prompt);
            if(input != null && input.equals("exit")) {
                connection.write("we're exiting\n");
                connection.close();
                return;
            }
            else {
                connection.write("=====> "+input+"\n");
            }
        }
        */
    }

    private static  void openBlocking(TelnetHandler telnetHandler) {
        NioEventLoopGroup group = new NioEventLoopGroup();
        DefaultChannelGroup channelGroup = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);

        //TelnetHandler telnetHandler = this;
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
     private static String reading(Connection connection, Readline readline, String prompt) {
        final String[] in = new String[1];
         CountDownLatch latch = new CountDownLatch(1);
        readline.readline(connection, prompt, input -> {
            in[0] = input;
            latch.countDown();
        });
        try {
            latch.await();
        }
        catch(InterruptedException e) {
            e.printStackTrace();
        }

         return in[0];
    }

    private static void read(Connection connection, Readline readline, String prompt) {
        readline.readline(connection, prompt, input -> {
            //we specify a simple lambda consumer to read the input thats returned
            if(input != null && input.equals("exit")) {
                connection.write("we're exiting\n");
                connection.close();
            }
            else {
                connection.write("=====> "+input+"\n");
                //lets read until we get exit
                read(connection, readline, prompt);
            }
        });
    }
}
