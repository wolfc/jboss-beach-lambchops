/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.beach.lambchops.server;

import org.jboss.beach.lambchops.common.ClassDefinition;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class Server {
    private static final Logger LOG = Logger.getLogger(Server.class.getName());

    public static void main(final String... args) {
        try {
            final ServerSocket serverSocket = new ServerSocket(14879);
            LOG.info("Server ready " + serverSocket);
            do {
                final Socket clientSocket = serverSocket.accept();
                final Thread thread = new Thread(() -> {
                    try {
                        final ServerClassLoader classLoader = new ServerClassLoader();
                        Thread.currentThread().setContextClassLoader(classLoader);
                        final ObjectInput input = new ObjectInputStream(clientSocket.getInputStream()) {
                            @Override
                            protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                                final String name = desc.getName();
                                try {
                                    return Class.forName(name, false, classLoader);
                                } catch (ClassNotFoundException ex) {
                                    return super.resolveClass(desc);
                                }
                            }
                        };
                        final ObjectOutput output = new ObjectOutputStream(clientSocket.getOutputStream());
                        do {
                            final Serializable s = (Serializable) input.readObject();
                            if (s instanceof ClassDefinition) {
                                classLoader.defineClass((ClassDefinition) s);
                            } else if (s instanceof Callable) {
                                final Object reply = ((Callable<?>) s).call();
                                output.writeObject(reply);
                                output.flush();
                            } else if (s instanceof Runnable) {
                                ((Runnable) s).run();
                            } else {
                                LOG.warning("Unknown message " + s);
                            }
                        } while(true);
                    } catch (EOFException e) {
                        LOG.info("Goodbye client " + clientSocket);
                    } catch (Exception e) {
                        LOG.warning("Client " + clientSocket + " died with " + e);
                        throw new RuntimeException(e);
                    }
                });
                thread.setDaemon(true);
                thread.start();
            } while(true);
        } catch (IOException e) {
            LOG.warning("Failed to start server: " + e);
            throw new RuntimeException(e);
        }
    }

    private static class ServerClassLoader extends ClassLoader {
        private void defineClass(ClassDefinition s) {
            final byte[] b = s.getClassBytes();
            defineClass(s.getClassName(), b, 0, b.length);
        }
    }
}
