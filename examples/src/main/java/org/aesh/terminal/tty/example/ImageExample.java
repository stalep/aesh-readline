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
package org.aesh.terminal.tty.example;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.image.ImageProtocol;
import org.aesh.terminal.image.TerminalImage;
import org.aesh.terminal.image.TerminalImageBuilder;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.TerminalConnection;
import org.aesh.terminal.utils.ANSI;

/**
 * Example demonstrating terminal image display.
 * <p>
 * Usage: java ImageExample [image_path]
 * <p>
 * If no image path is provided, a simple test pattern is generated.
 */
public class ImageExample {

    public ImageExample() {
    }

    /**
     * Main entry point for the image example.
     *
     * @param args command line arguments; optionally specify an image path
     */
    public static void main(String[] args) {
        try {
            TerminalConnection conn = new TerminalConnection();

            // Enter raw mode for immediate key handling
            Attributes savedAttributes = conn.enterRawMode();

            // Handle Ctrl+C signal
            conn.setSignalHandler(signal -> {
                if (signal == Signal.INT) {
                    conn.setAttributes(savedAttributes);
                    conn.write(ANSI.RESET);
                    conn.write("\nInterrupted!\n");
                    conn.close();
                }
            });

            // Clear screen and move cursor to home position
            conn.stdoutHandler().accept(ANSI.CLEAR_SCREEN);
            conn.write("\u001B[H"); // Move cursor to home (1,1)

            // Check for image support
            ImageProtocol protocol = conn.terminal().getImageProtocol();

            conn.write("\033[1;36m=== Terminal Image Example ===\033[0m\n\n");
            conn.write("Terminal type: " + conn.device().type() + "\n");
            conn.write("Image protocol: " + protocol + "\n\n");

            if (protocol == ImageProtocol.NONE) {
                conn.write("\033[1;33mNo image protocol support detected.\033[0m\n");
                conn.write("\nSupported terminals:\n");
                conn.write("  - Kitty, Ghostty, Konsole (Kitty graphics protocol)\n");
                conn.write("  - iTerm2, WezTerm, VSCode, Tabby, Hyper, Mintty (iTerm2 protocol)\n");
                conn.write("  - Windows Terminal, foot, contour, mlterm (Sixel protocol)\n");
                conn.write("\nPress any key to exit...\n");
            } else {
                // Get or generate image data
                byte[] imageData;
                String filename;

                if (args.length > 0) {
                    Path imagePath = Paths.get(args[0]);
                    if (!Files.exists(imagePath)) {
                        conn.write("\033[1;31mFile not found: " + args[0] + "\033[0m\n");
                        conn.write("\nPress any key to exit...\n");
                        setupExitHandler(conn, savedAttributes);
                        conn.openBlocking();
                        return;
                    }
                    imageData = Files.readAllBytes(imagePath);
                    filename = imagePath.getFileName().toString();
                    conn.write("Loading image: " + filename + " (" + imageData.length + " bytes)\n\n");
                } else {
                    // Generate a simple test PNG
                    imageData = generateTestPng();
                    filename = "test.png";
                    conn.write("No image specified, using generated test pattern.\n\n");
                }

                // Build and display the image
                TerminalImage image = TerminalImageBuilder.builder(conn.device())
                        .data(imageData)
                        .filename(filename)
                        .widthCells(40)
                        .build();

                if (image != null) {
                    conn.write(image.encode());
                    conn.write("\n\n");
                    conn.write("\033[1;32mImage displayed successfully!\033[0m\n");
                } else {
                    conn.write("\033[1;31mFailed to create image.\033[0m\n");
                }

                conn.write("\nPress any key to exit...\n");
            }

            setupExitHandler(conn, savedAttributes);
            conn.openBlocking();

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sets up an exit handler that restores terminal attributes and closes the connection
     * when any key is pressed.
     *
     * @param conn the terminal connection
     * @param savedAttributes the saved terminal attributes to restore
     */
    private static void setupExitHandler(TerminalConnection conn, Attributes savedAttributes) {
        conn.setStdinHandler(input -> {
            if (input != null && input.length > 0) {
                conn.setAttributes(savedAttributes);
                conn.write(ANSI.RESET);
                conn.write("Goodbye!\n");
                conn.close();
            }
        });
    }

    /**
     * Generate a test PNG with a colorful gradient pattern.
     * Creates a 64x64 pixel image using ImageIO.
     */
    private static byte[] generateTestPng() throws IOException {
        int size = 64;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int r = x * 255 / (size - 1);
                int g = y * 255 / (size - 1);
                int b = 128;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }
}
