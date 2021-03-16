//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.server;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Random;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.FileBufferedResponseHandler;
import org.eclipse.jetty.toolchain.test.FS;
import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class FileBufferedResponseHandlerTest
{
    private Server _server;
    private LocalConnector _local;
    private Path _testDir;
    private FileBufferedResponseHandler _bufferedHandler;

    @BeforeEach
    public void before() throws Exception
    {
        _testDir = MavenTestingUtils.getTargetTestingPath(FileBufferedResponseHandlerTest.class.getName());
        FS.ensureDirExists(_testDir);

        _server = new Server();
        HttpConfiguration config = new HttpConfiguration();
        config.setOutputBufferSize(1024);
        config.setOutputAggregationSize(256);
        _local = new LocalConnector(_server, new HttpConnectionFactory(config));
        _server.addConnector(_local);

        _bufferedHandler = new FileBufferedResponseHandler();
        _bufferedHandler.setTempDir(_testDir);
        _bufferedHandler.getPathIncludeExclude().include("/include/*");
        _bufferedHandler.getPathIncludeExclude().exclude("*.exclude");
        _bufferedHandler.getMimeIncludeExclude().exclude("text/excluded");

        ContextHandler contextHandler = new ContextHandler("/ctx");
        contextHandler.setHandler(_bufferedHandler);
        _server.setHandler(contextHandler);

        FS.ensureEmpty(_testDir);
    }

    @AfterEach
    public void after() throws Exception
    {
        _server.stop();
    }

    public int getNumFiles()
    {
        File[] files = _testDir.toFile().listFiles();
        if (files == null)
            return 0;

        return files.length;
    }

    @Test
    public void testPathNotIncluded() throws Exception
    {
        _bufferedHandler.setHandler(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
            {
                baseRequest.setHandled(true);
                response.setBufferSize(10);
                PrintWriter writer = response.getWriter();
                writer.println("a string larger than the buffer size");
                writer.println("Committed: " + response.isCommitted());
                writer.println("NumFiles: " + getNumFiles());
            }
        });

        _server.start();
        String rawResponse = _local.getResponse("GET /ctx/path HTTP/1.1\r\nHost: localhost\r\n\r\n");
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        String responseContent = response.getContent();

        // The response was committed after the first write and we never created a file to buffer the response into.
        assertThat(response.getStatus(), is(HttpStatus.OK_200));
        assertThat(responseContent, containsString("Committed: true"));
        assertThat(responseContent, containsString("NumFiles: 0"));
        assertThat(getNumFiles(), is(0));
    }

    @Test
    public void testIncluded() throws Exception
    {
        _bufferedHandler.setHandler(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
            {
                baseRequest.setHandled(true);
                response.setBufferSize(10);
                PrintWriter writer = response.getWriter();
                writer.println("a string larger than the buffer size");
                writer.println("Committed: " + response.isCommitted());
                writer.println("NumFiles: " + getNumFiles());
            }
        });

        _server.start();
        String rawResponse = _local.getResponse("GET /ctx/include/path HTTP/1.1\r\nHost: localhost\r\n\r\n");
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        String responseContent = response.getContent();

        // The response was not committed after the first write and a file was created to buffer the response.
        assertThat(response.getStatus(), is(HttpStatus.OK_200));
        assertThat(responseContent, containsString("Committed: false"));
        assertThat(responseContent, containsString("NumFiles: 1"));
        assertThat(getNumFiles(), is(0));
    }

    @Test
    public void testExcludedByPath() throws Exception
    {
        _bufferedHandler.setHandler(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
            {
                baseRequest.setHandled(true);
                response.setBufferSize(10);
                PrintWriter writer = response.getWriter();
                writer.println("a string larger than the buffer size");
                writer.println("Committed: " + response.isCommitted());
                writer.println("NumFiles: " + getNumFiles());
            }
        });

        _server.start();
        String rawResponse = _local.getResponse("GET /ctx/include/path.exclude HTTP/1.1\r\nHost: localhost\r\n\r\n");
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        String responseContent = response.getContent();

        // The response was committed after the first write and we never created a file to buffer the response into.
        assertThat(response.getStatus(), is(HttpStatus.OK_200));
        assertThat(responseContent, containsString("Committed: true"));
        assertThat(responseContent, containsString("NumFiles: 0"));
        assertThat(getNumFiles(), is(0));
    }

    @Test
    public void testExcludedByMime() throws Exception
    {
        String excludedMimeType = "text/excluded";
        _bufferedHandler.setHandler(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
            {
                baseRequest.setHandled(true);
                response.setContentType(excludedMimeType);
                response.setBufferSize(10);
                PrintWriter writer = response.getWriter();
                writer.println("a string larger than the buffer size");
                writer.println("Committed: " + response.isCommitted());
                writer.println("NumFiles: " + getNumFiles());
            }
        });

        _server.start();
        String rawResponse = _local.getResponse("GET /ctx/include/path HTTP/1.1\r\nHost: localhost\r\n\r\n");
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        String responseContent = response.getContent();

        // The response was committed after the first write and we never created a file to buffer the response into.
        assertThat(response.getStatus(), is(HttpStatus.OK_200));
        assertThat(responseContent, containsString("Committed: true"));
        assertThat(responseContent, containsString("NumFiles: 0"));
        assertThat(getNumFiles(), is(0));
    }

    @Test
    public void testFlushed() throws Exception
    {
        _bufferedHandler.setHandler(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
            {
                baseRequest.setHandled(true);
                response.setBufferSize(1024);
                PrintWriter writer = response.getWriter();
                writer.println("a string smaller than the buffer size");
                writer.println("NumFilesBeforeFlush: " + getNumFiles());
                writer.flush();
                writer.println("Committed: " + response.isCommitted());
                writer.println("NumFiles: " + getNumFiles());
            }
        });

        _server.start();
        String rawResponse = _local.getResponse("GET /ctx/include/path HTTP/1.1\r\nHost: localhost\r\n\r\n");
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        String responseContent = response.getContent();

        // The response was not committed after the buffer was flushed and a file was created to buffer the response.
        assertThat(response.getStatus(), is(HttpStatus.OK_200));
        assertThat(responseContent, containsString("NumFilesBeforeFlush: 0"));
        assertThat(responseContent, containsString("Committed: false"));
        assertThat(responseContent, containsString("NumFiles: 1"));
        assertThat(getNumFiles(), is(0));
    }

    @Test
    public void testClosed() throws Exception
    {
        _bufferedHandler.setHandler(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
            {
                baseRequest.setHandled(true);
                response.setBufferSize(10);
                PrintWriter writer = response.getWriter();
                writer.println("a string larger than the buffer size");
                writer.println("NumFiles: " + getNumFiles());
                writer.close();
                writer.println("writtenAfterClose");
            }
        });

        _server.start();
        String rawResponse = _local.getResponse("GET /ctx/include/path HTTP/1.1\r\nHost: localhost\r\n\r\n");
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        String responseContent = response.getContent();

        // The content written after close was not sent.
        assertThat(response.getStatus(), is(HttpStatus.OK_200));
        assertThat(responseContent, not(containsString("writtenAfterClose")));
        assertThat(responseContent, containsString("NumFiles: 1"));
        assertThat(getNumFiles(), is(0));
    }

    @Test
    public void testBufferSizeBig() throws Exception
    {
        int bufferSize = 4096;
        String largeContent = generateContent(bufferSize - 64);
        _bufferedHandler.setHandler(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
            {
                baseRequest.setHandled(true);
                response.setBufferSize(bufferSize);
                PrintWriter writer = response.getWriter();
                writer.println(largeContent);
                writer.println("Committed: " + response.isCommitted());
                writer.println("NumFiles: " + getNumFiles());
            }
        });

        _server.start();
        String rawResponse = _local.getResponse("GET /ctx/include/path HTTP/1.1\r\nHost: localhost\r\n\r\n");
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        String responseContent = response.getContent();

        // The content written was not buffered as a file as it was less than the buffer size.
        assertThat(response.getStatus(), is(HttpStatus.OK_200));
        assertThat(responseContent, not(containsString("writtenAfterClose")));
        assertThat(responseContent, containsString("Committed: false"));
        assertThat(responseContent, containsString("NumFiles: 0"));
        assertThat(getNumFiles(), is(0));
    }

    @Test
    public void testFlushEmpty() throws Exception
    {
        _bufferedHandler.setHandler(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
            {
                baseRequest.setHandled(true);
                response.setBufferSize(1024);
                PrintWriter writer = response.getWriter();
                writer.flush();
                int numFiles = getNumFiles();
                writer.println("NumFiles: " + numFiles);
            }
        });

        _server.start();
        String rawResponse = _local.getResponse("GET /ctx/include/path HTTP/1.1\r\nHost: localhost\r\n\r\n");
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        String responseContent = response.getContent();

        // The flush should not create the file unless there is content to write.
        assertThat(response.getStatus(), is(HttpStatus.OK_200));
        assertThat(responseContent, containsString("NumFiles: 0"));
        assertThat(getNumFiles(), is(0));
    }

    @Test
    public void testReset() throws Exception
    {
        _bufferedHandler.setHandler(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
            {
                baseRequest.setHandled(true);
                response.setBufferSize(8);
                PrintWriter writer = response.getWriter();
                writer.println("THIS WILL BE RESET");
                writer.flush();
                writer.println("THIS WILL BE RESET");
                int numFilesBeforeReset = getNumFiles();
                response.resetBuffer();
                int numFilesAfterReset = getNumFiles();

                writer.println("NumFilesBeforeReset: " + numFilesBeforeReset);
                writer.println("NumFilesAfterReset: " + numFilesAfterReset);
                writer.println("a string larger than the buffer size");
                writer.println("NumFilesAfterWrite: " + getNumFiles());
            }
        });

        _server.start();
        String rawResponse = _local.getResponse("GET /ctx/include/path HTTP/1.1\r\nHost: localhost\r\n\r\n");
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);
        String responseContent = response.getContent();

        // Resetting the response buffer will delete the file.
        assertThat(response.getStatus(), is(HttpStatus.OK_200));
        assertThat(responseContent, not(containsString("THIS WILL BE RESET")));
        assertThat(responseContent, containsString("NumFilesBeforeReset: 1"));
        assertThat(responseContent, containsString("NumFilesAfterReset: 0"));
        assertThat(responseContent, containsString("NumFilesAfterWrite: 1"));
        assertThat(getNumFiles(), is(0));
    }

    private static String generateContent(int size)
    {
        Random random = new Random();
        StringBuilder stringBuilder = new StringBuilder(size);
        for (int i = 0; i < size; i++)
        {
            stringBuilder.append((char)Math.abs(random.nextInt(0x7F)));
        }
        return stringBuilder.toString();
    }
}
