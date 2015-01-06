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

import org.junit.Test;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class SimpleTestCase {
    @Test
    public void testCallable() throws IOException, ClassNotFoundException {
        final Callable<String> s = (Callable<String> & Serializable) () -> { return "The result"; };

        try(final Client client = new Client("localhost")) {
            final String response = client.send(s);
            assertEquals("The result", response);
        }
    }

    @Test
    public void testRunnable() throws IOException {
        final Runnable s = (Runnable & Serializable) () -> { System.out.println("Hello world"); };

        try(final Client client = new Client("localhost")) {
            client.send(s);
        }
    }
}
