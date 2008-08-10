/*
 * Copyright (c) 2008, Esko Luontola. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice,
 *       this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.orfjackal.darkstar.rpc.comm;

import com.sun.sgs.app.*;
import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import jdave.Specification;
import jdave.junit4.JDaveRunner;
import net.orfjackal.darkstar.integration.DarkstarServer;
import net.orfjackal.darkstar.integration.DebugClient;
import net.orfjackal.darkstar.integration.util.TempDirectory;
import net.orfjackal.darkstar.integration.util.TimedInterrupt;
import net.orfjackal.darkstar.rpc.ServiceHelper;
import org.junit.runner.RunWith;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * @author Esko Luontola
 * @since 18.6.2008
 */
@RunWith(JDaveRunner.class)
public class DarkstarIntegrationSpec extends Specification<Object> {

    private static final String STARTUP_MSG = "RpcTest has started";
    private static final byte LOCATE_SERVICE_CMD = 0x01;
    private static final int TIMEOUT = 5000;

    public class WhenUsingARealDarkstarServer {

        private DarkstarServer server;
        private DebugClient client;
        private RpcGateway gatewayOnClient;

        private TempDirectory tempDirectory;
        private Thread testTimeout;

        public Object create() throws Exception {
            tempDirectory = new TempDirectory();
            tempDirectory.create();

            server = new DarkstarServer(tempDirectory.getDirectory());
            server.setAppName("RpcTest");
            server.setAppListener(RpcTestAppListener.class);
            server.start();

            final ClientChannelAdapter adapter = new ClientChannelAdapter();
            gatewayOnClient = adapter.getGateway();
            client = new DebugClient("localhost", server.getPort()) {
                public ClientChannelListener joinedChannel(ClientChannel channel) {
                    return adapter.joinedChannel(channel);
                }
            };

            // wait for the server to start up before running the tests
            server.waitUntilSystemOutContains(STARTUP_MSG, TIMEOUT);

            // needed to avoid blocking on client.events.take()
            testTimeout = TimedInterrupt.startOnCurrentThread(TIMEOUT);

            // wait for the client to log in
            client.login();
            String event = client.events.take();
            specify(event, event.startsWith(DebugClient.LOGGED_IN));

            // wait for the RPC channel to be established
            event = client.events.take();
            specify(event, event.startsWith(DebugClient.JOINED_CHANNEL));

            return null;
        }

        public void destroy() {
            try {
                System.out.println("client.events = " + client.events);
                System.out.println("client.messages = " + client.messages);
                System.out.println("Server Out:");
                System.out.println(server.getSystemOut());
                System.err.println("Server Log:");
                System.err.println(server.getSystemErr());
                testTimeout.interrupt();
                client.logout(true);
            } finally {
                server.shutdown();
                tempDirectory.dispose();
            }
        }

        public void rpcMethodsOnServerMayBeCalledFromClient() throws Exception {

            // locate the RPC service on server
            Set<Echo> services = gatewayOnClient.remoteFindByType(Echo.class);
            specify(services.size(), should.equal(1));
            Echo echoOnServer = services.iterator().next();

            // call methods on the RPC service
            Future<String> f = echoOnServer.echo("hello");
            String result = f.get();
            specify(result, should.equal("hello, hello"));
        }

//        public void rpcMethodsOnClientMayBeCalledFromServer() throws Exception {
//
//            // command the server to locate the RPC service on the client
//            client.send((ByteBuffer) ByteBuffer.allocate(1).put(LOCATE_SERVICE_CMD).flip());
//            server.waitUntilSystemOutContains("echoOnClient = not null", TIMEOUT);
//
//            // command the server to call methods on the RPC service
//            // TODO
//        }

    }

    // Interface implementations for connecting to Darkstar

    public static class RpcTestAppListener implements AppListener, Serializable {
        private static final long serialVersionUID = 1L;

        public void initialize(Properties props) {
            System.out.println(STARTUP_MSG);
        }

        public ClientSessionListener loggedIn(ClientSession session) {
            return new RpcTestClientSessionListener(session);
        }
    }

    private static class RpcTestClientSessionListener implements ClientSessionListener, ManagedObject, Serializable {
        private static final long serialVersionUID = 1L;

        private final ManagedReference<ClientSession> session;
        private final RpcGateway gateway;
        private Set<Echo> echoOnClient;

        public RpcTestClientSessionListener(ClientSession session) {
            this.session = AppContext.getDataManager().createReference(session);
            gateway = initGateway(session);
            gateway.registerService(Echo.class, new EchoImpl());
        }

        private RpcGateway initGateway(ClientSession session) {
            ChannelAdapter adapter = new ChannelAdapter(TIMEOUT / 2);
            rpcChannelForClient(session, adapter);
            return adapter.getGateway();
        }

        private static void rpcChannelForClient(ClientSession session, ChannelAdapter adapter) {
            Channel channel = AppContext.getChannelManager()
                    .createChannel("RpcChannel:" + session.getName(), adapter, Delivery.RELIABLE);
            channel.join(session);
            adapter.setChannel(channel);
        }

        public void receivedMessage(ByteBuffer message) {
            byte command = message.get();
            if (command == LOCATE_SERVICE_CMD) {
                System.out.println("command = LOCATE_SERVICE_CMD");
                echoOnClient = gateway.remoteFindByType(Echo.class);
                System.out.println("echoOnClient = " + (echoOnClient == null ? "null" : "not null"));

            } else {
                System.out.println("Unknown command: " + command);
            }
        }

        public void disconnected(boolean graceful) {
        }
    }

    // Application logic

    public interface Echo {
        Future<String> echo(String s);
    }

    public static class EchoImpl implements Echo, Serializable {
        private static final long serialVersionUID = 1L;

        public Future<String> echo(String s) {
            return ServiceHelper.wrap(s + ", " + s);
        }
    }
}
