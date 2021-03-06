/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.transport.http.netty.contractimpl.websocket;

import org.wso2.carbon.messaging.exceptions.ClientConnectorException;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketClientConnector;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketConnectorListener;
import org.wso2.carbon.transport.http.netty.listener.WebSocketSourceHandler;
import org.wso2.carbon.transport.http.netty.sender.websocket.WebSocketClient;

import java.net.URISyntaxException;
import java.util.Map;
import javax.net.ssl.SSLException;
import javax.websocket.Session;

/**
 * Implementation of WebSocket client connector.
 */
public class WebSocketClientConnectorImpl implements WebSocketClientConnector {

    private final String remoteUrl;
    private final String subProtocol;
    private final String target;
    private final WebSocketSourceHandler sourceHandler;
    private final boolean allowExtensions;

    public WebSocketClientConnectorImpl(String remoteUrl, String target, String subProtocol, boolean allowExtensions,
                                        WebSocketSourceHandler sourceHandler) {
        this.remoteUrl = remoteUrl;
        this.target = target;
        this.subProtocol = subProtocol;
        this.sourceHandler = sourceHandler;
        this.allowExtensions = allowExtensions;
    }

    @Override
    public Session connect(WebSocketConnectorListener connectorListener, Map<String, String> customHeaders)
            throws ClientConnectorException {

        WebSocketClient webSocketClient = new WebSocketClient(remoteUrl, target, subProtocol, allowExtensions,
                                                              customHeaders, sourceHandler, connectorListener);
        try {
            return webSocketClient.handshake();
        } catch (InterruptedException e) {
            throw new ClientConnectorException("Handshake interrupted", e);
        } catch (URISyntaxException e) {
            throw new ClientConnectorException("SSL Exception occurred during handshake", e);
        } catch (SSLException e) {
            throw new ClientConnectorException("URI Syntax exception occurred during handshake", e);
        }
    }
}
