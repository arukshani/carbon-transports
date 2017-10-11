/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.wso2.carbon.transport.http.netty.redirect;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.transport.http.netty.common.Constants;
import org.wso2.carbon.transport.http.netty.config.SenderConfiguration;
import org.wso2.carbon.transport.http.netty.config.TransportsConfiguration;
import org.wso2.carbon.transport.http.netty.contract.HttpClientConnector;
import org.wso2.carbon.transport.http.netty.contract.HttpResponseFuture;
import org.wso2.carbon.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.carbon.transport.http.netty.contractimpl.HttpWsConnectorFactoryImpl;
import org.wso2.carbon.transport.http.netty.https.HTTPSConnectorListener;
import org.wso2.carbon.transport.http.netty.message.HTTPCarbonMessage;
import org.wso2.carbon.transport.http.netty.message.HTTPConnectorUtil;
import org.wso2.carbon.transport.http.netty.message.HttpMessageDataStreamer;
import org.wso2.carbon.transport.http.netty.sender.RedirectHandler;
import org.wso2.carbon.transport.http.netty.util.TestUtil;
import org.wso2.carbon.transport.http.netty.util.server.HttpServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * Test cases for redirection scenarios
 */
public class HTTPClientRedirectTestCase {

    private HttpServer httpServer;
    private HttpServer redirectServer;
    private HttpClientConnector httpClientConnector;
    HttpWsConnectorFactory connectorFactory;
    TransportsConfiguration transportsConfiguration;
    public static final String FINAL_DESTINATION = "http://localhost:9000/destination";
    public static final String RELATIVE_REDIRECT_URL1 = "/redirect2";
    public static final int REDIRECT_DESTINATION_PORT1 = 9091;
    public static final int REDIRECT_DESTINATION_PORT2 = 9092;
    public static final String ABSOLUTE_REDIRECT_URL = "http://localhost:9092/redirect2";
    public static final String URL1 = "http://www.mocky.io/v2/59d590762700000a049cd694";
    public static final String URL2 = "http://www.mocky.io/v3/59d590762700000a049cd694";

    private String testValue = "Test Message";
    private String testValueForLoopRedirect = "Test Loop";

    @BeforeClass
    public void setup() {
        transportsConfiguration = TestUtil
                .getConfiguration("/simple-test-config" + File.separator + "netty-transports.yml");

        connectorFactory = new HttpWsConnectorFactoryImpl();

        SenderConfiguration senderConfiguration = HTTPConnectorUtil
                .getSenderConfiguration(transportsConfiguration, Constants.HTTP_SCHEME);
        senderConfiguration.setFollowRedirect(true);
        senderConfiguration.setMaxRedirectCount(5);

        httpClientConnector = connectorFactory
                .createHttpClientConnector(HTTPConnectorUtil.getTransportProperties(transportsConfiguration),
                        senderConfiguration);

    }

    @Test
    public void unitTestForRedirectHandler() throws URISyntaxException, IOException {
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        embeddedChannel.pipeline().addLast(new HttpResponseDecoder());
        embeddedChannel.pipeline().addLast(new HttpRequestEncoder());
        embeddedChannel.pipeline().addLast(new RedirectHandler(null, false, 5));
        HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TEMPORARY_REDIRECT,
                Unpooled.EMPTY_BUFFER);
        response.headers().set(HttpHeaders.Names.LOCATION, FINAL_DESTINATION);
        embeddedChannel.attr(Constants.ORIGINAL_REQUEST)
                .set(createHttpRequest(Constants.HTTP_GET_METHOD, FINAL_DESTINATION));
        embeddedChannel.writeInbound(response);
        embeddedChannel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
        assertNotNull(embeddedChannel.readOutbound());
    }

    @Test
    public void unitTestForRedirectLoop() throws URISyntaxException, IOException {
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        embeddedChannel.pipeline().addLast(new HttpResponseDecoder());
        embeddedChannel.pipeline().addLast(new HttpRequestEncoder());
        embeddedChannel.pipeline().addLast(new RedirectHandler(null, false, 5));
        HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TEMPORARY_REDIRECT,
                Unpooled.EMPTY_BUFFER);
        response.headers().set(HttpHeaders.Names.LOCATION, ABSOLUTE_REDIRECT_URL);
        embeddedChannel.attr(Constants.ORIGINAL_REQUEST)
                .set(createHttpRequest(Constants.HTTP_POST_METHOD, ABSOLUTE_REDIRECT_URL));
        embeddedChannel.attr(Constants.REDIRECT_COUNT).set(new AtomicInteger(5));
        embeddedChannel.writeInbound(response);
        embeddedChannel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
        assertNull(embeddedChannel.readOutbound());
    }

    /**
     * Unit test for redirect code 303.
     */
    @Test
    public void unitTestFor303() {
        try {
            RedirectHandler redirectHandler = new RedirectHandler(null, false, 0);
            Method method = RedirectHandler.class
                    .getDeclaredMethod("getRedirectState", String.class, Integer.TYPE, HTTPCarbonMessage.class);
            method.setAccessible(true);
            Map<String, String> returnValue = (Map<String, String>) method
                    .invoke(redirectHandler, FINAL_DESTINATION, HttpResponseStatus.SEE_OTHER.code(),
                            createHttpRequest(Constants.HTTP_POST_METHOD, FINAL_DESTINATION));
            assertEquals(Constants.HTTP_GET_METHOD, returnValue.get(Constants.HTTP_METHOD));
        } catch (NoSuchMethodException e) {
            TestUtil.handleException("NoSuchMethodException occurred while running unitTestFor300", e);
        } catch (InvocationTargetException e) {
            TestUtil.handleException("InvocationTargetException occurred while running unitTestFor300", e);
        } catch (IllegalAccessException e) {
            TestUtil.handleException("IllegalAccessException occurred while running unitTestFor300", e);
        }
    }

    /**
     * This unit test is applicable to redirect status codes 300, 305, 307 ans 308.
     */
    @Test
    public void unitTestForOriginalMethod() {
        try {
            RedirectHandler redirectHandler = new RedirectHandler(null, false, 0);
            Method method = RedirectHandler.class
                    .getDeclaredMethod("getRedirectState", String.class, Integer.TYPE, HTTPCarbonMessage.class);
            method.setAccessible(true);
            Map<String, String> returnValue = (Map<String, String>) method
                    .invoke(redirectHandler, FINAL_DESTINATION, HttpResponseStatus.TEMPORARY_REDIRECT.code(),
                            createHttpRequest(Constants.HTTP_HEAD_METHOD, FINAL_DESTINATION));
            assertEquals(Constants.HTTP_HEAD_METHOD, returnValue.get(Constants.HTTP_METHOD));
        } catch (NoSuchMethodException e) {
            TestUtil.handleException("NoSuchMethodException occurred while running unitTestForOriginalMethod", e);
        } catch (InvocationTargetException e) {
            TestUtil.handleException("InvocationTargetException occurred while running unitTestForOriginalMethod", e);
        } catch (IllegalAccessException e) {
            TestUtil.handleException("IllegalAccessException occurred while running unitTestForOriginalMethod", e);
        }
    }

    /**
     * This unit test is applicable to redirect status codes 301 and 302.
     */
    @Test
    public void unitTestForAlwaysGet() {
        try {
            RedirectHandler redirectHandler = new RedirectHandler(null, false, 0);
            Method method = RedirectHandler.class
                    .getDeclaredMethod("getRedirectState", String.class, Integer.TYPE, HTTPCarbonMessage.class);
            method.setAccessible(true);
            Map<String, String> returnValue = (Map<String, String>) method
                    .invoke(redirectHandler, FINAL_DESTINATION, HttpResponseStatus.MOVED_PERMANENTLY.code(),
                            createHttpRequest(Constants.HTTP_HEAD_METHOD, FINAL_DESTINATION));
            assertEquals(Constants.HTTP_GET_METHOD, returnValue.get(Constants.HTTP_METHOD));
        } catch (NoSuchMethodException e) {
            TestUtil.handleException("NoSuchMethodException occurred while running unitTestForAlwaysGet", e);
        } catch (InvocationTargetException e) {
            TestUtil.handleException("InvocationTargetException occurred while running unitTestForAlwaysGet", e);
        } catch (IllegalAccessException e) {
            TestUtil.handleException("IllegalAccessException occurred while running unitTestForAlwaysGet", e);
        }
    }

    /**
     * Original request and the redirect request goes to two different domains.
     */
    @Test
    public void unitTestToDetermineCrossDomainURLs() {
        RedirectHandler redirectHandler = new RedirectHandler(null, false, 0);
        try {
            Method method = RedirectHandler.class
                    .getDeclaredMethod("isCrossDomain", String.class, HTTPCarbonMessage.class);
            method.setAccessible(true);
            boolean isCrossDomainURL = (boolean) method.invoke(redirectHandler, ABSOLUTE_REDIRECT_URL,
                    createHttpRequest(Constants.HTTP_HEAD_METHOD, URL1));
            assertEquals(true, isCrossDomainURL);
        } catch (NoSuchMethodException e) {
            TestUtil.handleException("NoSuchMethodException occurred while running unitTestToDetermineCrossDomainURLs",
                    e);
        } catch (InvocationTargetException e) {
            TestUtil.handleException(
                    "InvocationTargetException occurred while running unitTestToDetermineCrossDomainURLs", e);
        } catch (IllegalAccessException e) {
            TestUtil.handleException("IllegalAccessException occurred while running unitTestToDetermineCrossDomainURLs",
                    e);
        }

    }

    /**
     * Original request and the redirect request uses the same domain.
     */
    @Test
    public void unitTestForSameDomain() {
        RedirectHandler redirectHandler = new RedirectHandler(null, false, 0);
        try {
            Method method = RedirectHandler.class
                    .getDeclaredMethod("isCrossDomain", String.class, HTTPCarbonMessage.class);
            method.setAccessible(true);
            boolean isCrossDomainURL = (boolean) method
                    .invoke(redirectHandler, URL1, createHttpRequest(Constants.HTTP_HEAD_METHOD, URL2));
            assertEquals(false, isCrossDomainURL);
        } catch (NoSuchMethodException e) {
            TestUtil.handleException("NoSuchMethodException occurred while running unitTestForSameDomain", e);
        } catch (InvocationTargetException e) {
            TestUtil.handleException("InvocationTargetException occurred while running unitTestForSameDomain", e);
        } catch (IllegalAccessException e) {
            TestUtil.handleException("IllegalAccessException occurred while running unitTestForSameDomain", e);
        }

    }

    /**
     * Test for single redirection in cross domain situation where the location is defined as an absolute path.
     */
    @Test
    public void integrationTestForSingleRedirect() {
        try {

            httpServer = TestUtil.startHTTPServer(TestUtil.TEST_HTTP_SERVER_PORT, testValue, Constants.TEXT_PLAIN);

            redirectServer = TestUtil
                    .startHTTPServerForRedirect(REDIRECT_DESTINATION_PORT1, testValue, Constants.TEXT_PLAIN,
                            HttpResponseStatus.TEMPORARY_REDIRECT.code(), FINAL_DESTINATION, 0);

            CountDownLatch latch = new CountDownLatch(1);
            HTTPSConnectorListener listener = new HTTPSConnectorListener(latch);
            HttpResponseFuture responseFuture = httpClientConnector
                    .send(createHttpCarbonRequest(null, REDIRECT_DESTINATION_PORT1));
            responseFuture.setHttpConnectorListener(listener);

            latch.await(60, TimeUnit.SECONDS);

            HTTPCarbonMessage response = listener.getHttpResponseMessage();
            assertNotNull(response);
            String result = new BufferedReader(
                    new InputStreamReader(new HttpMessageDataStreamer(response).getInputStream())).lines()
                    .collect(Collectors.joining("\n"));

            assertEquals(testValue, result);
            redirectServer.shutdown();
            httpServer.shutdown();
        } catch (Exception e) {
            TestUtil.handleException("Exception occurred while running singleRedirectionTest", e);
        }
    }

    /**
     * Test for redirection loop.
     */
    @Test
    public void integrationTestForRedirectLoop() {
        try {
            SenderConfiguration senderConfiguration = HTTPConnectorUtil
                    .getSenderConfiguration(transportsConfiguration, Constants.HTTP_SCHEME);
            senderConfiguration.setFollowRedirect(true);
            senderConfiguration.setMaxRedirectCount(2);

            HttpClientConnector httpClientConnector = connectorFactory
                    .createHttpClientConnector(HTTPConnectorUtil.getTransportProperties(transportsConfiguration),
                            senderConfiguration);

            HttpServer redirectServer1 = TestUtil
                    .startHTTPServerForRedirect(REDIRECT_DESTINATION_PORT1, testValue, Constants.TEXT_PLAIN,
                            HttpResponseStatus.TEMPORARY_REDIRECT.code(), ABSOLUTE_REDIRECT_URL, 0);

            HttpServer redirectServer2 = TestUtil
                    .startHTTPServerForRedirect(REDIRECT_DESTINATION_PORT2, testValueForLoopRedirect,
                            Constants.TEXT_PLAIN, HttpResponseStatus.TEMPORARY_REDIRECT.code(),
                            RELATIVE_REDIRECT_URL1, 0);

            CountDownLatch latch = new CountDownLatch(1);
            HTTPSConnectorListener listener = new HTTPSConnectorListener(latch);
            HttpResponseFuture responseFuture = httpClientConnector
                    .send(createHttpCarbonRequest(null, REDIRECT_DESTINATION_PORT1));
            responseFuture.setHttpConnectorListener(listener);

            latch.await(60, TimeUnit.SECONDS);

            HTTPCarbonMessage response = listener.getHttpResponseMessage();
            assertNotNull(response);
            String result = new BufferedReader(
                    new InputStreamReader(new HttpMessageDataStreamer(response).getInputStream())).lines()
                    .collect(Collectors.joining("\n"));

            assertEquals(testValueForLoopRedirect, result);
            redirectServer1.shutdown();
            redirectServer2.shutdown();

        } catch (Exception e) {
            TestUtil.handleException("Exception occurred while running testRedirectionLoop", e);
        }
    }

    /**
     * In case of a timeout during redirection, check whether the proper error response is sent to the client.
     */
    @Test
    public void integrationTestForTimeout() {
        try {

            SenderConfiguration senderConfiguration = HTTPConnectorUtil
                    .getSenderConfiguration(transportsConfiguration, Constants.HTTP_SCHEME);
            senderConfiguration.setFollowRedirect(true);
            senderConfiguration.setMaxRedirectCount(5);
            senderConfiguration.setSocketIdleTimeout(2000);

            HttpClientConnector httpClientConnector = connectorFactory
                    .createHttpClientConnector(HTTPConnectorUtil.getTransportProperties(transportsConfiguration),
                            senderConfiguration);

            HttpServer httpServer = TestUtil.startHTTPServer(TestUtil.TEST_HTTP_SERVER_PORT, testValue, Constants
                    .TEXT_PLAIN);

            HttpServer redirectServer = TestUtil
                    .startHTTPServerForRedirect(REDIRECT_DESTINATION_PORT1, testValue, Constants.TEXT_PLAIN,
                            HttpResponseStatus.TEMPORARY_REDIRECT.code(), FINAL_DESTINATION, 3000);

            CountDownLatch latch = new CountDownLatch(1);
            HTTPSConnectorListener listener = new HTTPSConnectorListener(latch);
            HttpResponseFuture responseFuture = httpClientConnector
                    .send(createHttpCarbonRequest(null, REDIRECT_DESTINATION_PORT1));
            responseFuture.setHttpConnectorListener(listener);

            latch.await(60, TimeUnit.SECONDS);

            HTTPCarbonMessage response = listener.getHttpResponseMessage();
            assertNotNull(response);
            assertEquals(101504, response.getMessagingException().getErrorCode());
            redirectServer.shutdown();
            httpServer.shutdown();
        } catch (Exception e) {
            TestUtil.handleException("Exception occurred while running integrationTestForTimeout", e);
        }
    }

    private HTTPCarbonMessage createHttpCarbonRequest(String requestUrl, int destinationPort) {
        HTTPCarbonMessage msg = new HTTPCarbonMessage(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, ""));
        msg.setProperty(Constants.PORT, destinationPort);
        msg.setProperty(Constants.PROTOCOL, "http");
        msg.setProperty(Constants.HOST, "localhost");
        msg.setProperty(Constants.HTTP_METHOD, Constants.HTTP_GET_METHOD);
        if (requestUrl != null) {
            msg.setProperty(Constants.REQUEST_URL, requestUrl);
        }
        msg.setEndOfMsgAdded(true);
        return msg;
    }

    private HTTPCarbonMessage createHttpRequest(String method, String location) {
        URL locationUrl = null;
        try {
            locationUrl = new URL(location);
        } catch (MalformedURLException e) {
            TestUtil.handleException("MalformedURLException occurred while running unitTestForRedirectHandler ", e);
        }

        HttpMethod httpMethod = new HttpMethod(method);
        HTTPCarbonMessage httpCarbonRequest = new HTTPCarbonMessage(
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, httpMethod, ""));
        httpCarbonRequest.setProperty(Constants.PORT, locationUrl.getPort());
        httpCarbonRequest.setProperty(Constants.PROTOCOL, locationUrl.getProtocol());
        httpCarbonRequest.setProperty(Constants.HOST, locationUrl.getHost());
        httpCarbonRequest.setProperty(Constants.HTTP_METHOD, method);
        httpCarbonRequest.setProperty(Constants.REQUEST_URL, locationUrl.getPath());
        httpCarbonRequest.setProperty(Constants.TO, locationUrl.getPath());

        httpCarbonRequest.setHeader(Constants.HOST, locationUrl.getHost());
        httpCarbonRequest.setHeader(Constants.PORT, Integer.toString(locationUrl.getPort()));
        httpCarbonRequest.setEndOfMsgAdded(true);
        return httpCarbonRequest;
    }

}