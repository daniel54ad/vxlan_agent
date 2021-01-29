/*
 * Copyright 2021-present Open Networking Foundation
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
package nctu.winlab.vxlanagent;

import com.google.common.collect.ImmutableSet;

import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onlab.packet.VXLAN;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javassist.bytecode.stackmap.BasicBlock.Catch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Dictionary;
import java.util.Properties;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.io.NoCloseOutputStream;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {SomeInterface.class},
           property = {
               "someProperty=Some Default String Value",
           })
public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    private String someProperty;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    private ApplicationId appId;
    private VxlanAgentPacketProcessor processor = new VxlanAgentPacketProcessor();

    private static final String REMOTE_HOST = "140.113.194.249";
    private static final String USERNAME = "arcadyan";
    private static final String PASSWORD = "arcadyan";
    private static final int REMOTE_PORT = 22;
    private static final int SESSION_TIMEOUT = 10000;
    private static final int CHANNEL_TIMEOUT = 5000;

    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());
        appId = coreService.registerApplication("nctu.winlab.bridge");
        packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();
        createVxlanInterface();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        withdrawIntercepts();
        packetService.removeProcessor(processor);
        processor = null;
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }

    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector
            .matchEthType(Ethernet.TYPE_IPV4)
            .matchIPProtocol(IPv4.PROTOCOL_UDP)
            .matchUdpDst(TpPort.tpPort(4789));
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector
            .matchEthType(Ethernet.TYPE_IPV4)
            .matchIPProtocol(IPv4.PROTOCOL_UDP)
            .matchUdpDst(TpPort.tpPort(4789));
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    private class VxlanAgentPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }
            InboundPacket pkt = context.inPacket();
            
            Ethernet ethPkt = pkt.parsed();
            if (ethPkt.getEtherType() != Ethernet.TYPE_IPV4) return;
            
            IPv4 ipPkt = (IPv4)ethPkt.getPayload();
            if (ipPkt.getProtocol() != IPv4.PROTOCOL_UDP) return;
            
            UDP udpPkt = (UDP)ipPkt.getPayload();
            if (udpPkt.getDestinationPort() != 4789) return;
            
            VXLAN vxlanPkt = (VXLAN)udpPkt.getPayload();
            int vni = vxlanPkt.getVni();
            int srcPort = udpPkt.getSourcePort();
            createVxlanInterface();
        }
    }

    private void createVxlanInterface() {
        String remoteShellScript = "/home/daniel/Desktop/test.sh";
        try(SshClient client = SshClient.setUpDefaultClient()) {
            client.start();

            try(ClientSession session = client.connect(USERNAME, REMOTE_HOST, REMOTE_PORT).verify().getSession()) {
                session.addPasswordIdentity(PASSWORD);
                session.auth().verify(SESSION_TIMEOUT);

                try(ChannelExec channel = session.createExecChannel("sh /home/arcadyan/Desktop/test.sh")) {
                    OutputStream stdout = new NoCloseOutputStream(System.out);
                    OutputStream stderr = new NoCloseOutputStream(System.err);

                    channel.setOut(stdout);
                    channel.setErr(stderr);
                    channel.open().verify(CHANNEL_TIMEOUT);
                } catch (Exception e3) {

                }
            } catch (Exception e1) {

            }
        } catch (Exception e2) {

        }
    }
}
