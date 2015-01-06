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
package org.jboss.beach.lambchops.client;

import org.jboss.beach.lambchops.common.ClassDefinition;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class Client implements AutoCloseable {
    private final int DEFAULT_PORT = 14879;

    private final Socket socket;
    private final ObjectOutput output;
    private final ObjectInput input;

    // classes already sent over in a/b/c format
    private final Set<String> sent = new HashSet<>();

    public Client(final String host) throws IOException {
        socket = new Socket(host, DEFAULT_PORT);
        output = new ObjectOutputStream(socket.getOutputStream());
        input = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void close() throws IOException {
        output.flush();
        output.close();
        input.close();
        socket.close();
    }

    private static byte[] load(final URL resource) throws IOException {
        try(final BufferedInputStream in = new BufferedInputStream(resource.openStream())) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[4096];
            int len;
            do {
                len = in.read(buffer);
                if (len > 0)
                    out.write(buffer, 0, len);
            } while (len >= 0);
            return out.toByteArray();
        }
    }

    public <V> V send(final Callable<V> callable) throws IOException, ClassNotFoundException {
        sendObject(callable);
        final V reply = (V) input.readObject();
        return reply;
    }

    public void send(final Runnable runnable) throws IOException {
        sendObject(runnable);
    }

    protected void sendObject(final Object msg) throws IOException {
        // Find out which class is going to be needed on the other side
        final ObjectOutputStream out = new ClientObjectOutput() {
            @Override
            protected Object replaceObject(Object obj) throws IOException {
                Class<?> cls = obj.getClass();
                if (cls.isArray())
                    cls = cls.getComponentType();
                /*
                try {
                    Class.forName(cls.getCanonicalName());
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
                */
                if (cls.getProtectionDomain().getCodeSource() != null) {
                    throw new RuntimeException("NYI: sending over class " + cls);
                } else if (SerializedLambda.class.isAssignableFrom(cls)) {
                    final SerializedLambda lambda = (SerializedLambda) obj;
                    final String capturingClass = lambda.getCapturingClass();
                    if (!sent.contains(capturingClass)) {
                        final byte[] classBytes = load(cls.getResource("/" + capturingClass + ".class"));
                        final ClassDefinition classDefinition = new ClassDefinition(capturingClass.replace('/', '.'), classBytes);
                        output.writeObject(classDefinition);
                        sent.add(capturingClass);
                    }
                }
                return super.replaceObject(obj);
            }
        };
        out.writeObject(msg);
        /*
        try {
            classLoader.loadClass(msg.getClass().getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        */
        output.writeObject(msg);
        output.flush();
    }

    private static class ClientObjectOutput extends ObjectOutputStream {
        protected ClientObjectOutput() throws IOException, SecurityException {
            super(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    // do nothing
                }
            });
            enableReplaceObject(true);
        }
    }
}
