/*
 * Copyright 2017-present Open Networking Foundation
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

package org.opencord.igmpproxy;

import static org.junit.Assert.assertEquals;
import static org.onlab.junit.TestTools.assertAfter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onlab.junit.TestUtils;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onosproject.core.CoreServiceAdapter;
import org.onosproject.net.flow.FlowRuleServiceAdapter;
import org.onosproject.net.flowobjective.FlowObjectiveServiceAdapter;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class IgmpStatisticsTest extends IgmpManagerBase {

    private static final int WAIT_TIMEOUT = 500;

    private IgmpManager igmpManager;
    private final Logger log = LoggerFactory.getLogger(getClass());

    private IgmpStatisticsManager igmpStatisticsManager;

    // Set up the IGMP application.
    @Before
    public void setUp() {
        igmpManager = new IgmpManager();
        igmpManager.coreService = new CoreServiceAdapter();
        igmpManager.mastershipService = new MockMastershipService();
        igmpManager.flowObjectiveService = new FlowObjectiveServiceAdapter();
        igmpManager.deviceService = new MockDeviceService();
        igmpManager.packetService = new MockPacketService();
        igmpManager.flowRuleService = new FlowRuleServiceAdapter();
        igmpManager.multicastService = new TestMulticastRouteService();
        igmpManager.sadisService = new MockSadisService();
        igmpStatisticsManager = new IgmpStatisticsManager();
        igmpStatisticsManager.cfgService = new MockCfgService();
        TestUtils.setField(igmpStatisticsManager, "eventDispatcher", new TestEventDispatcher());
        igmpStatisticsManager.activate();
        igmpManager.igmpStatisticsManager = this.igmpStatisticsManager;
        // By default - we send query messages
        SingleStateMachine.sendQuery = true;
    }

    // Tear Down the IGMP application.
    @After
    public void tearDown() {
        igmpManager.deactivate();
        IgmpManager.groupMemberMap.clear();
        StateMachine.clearMap();
    }

    //Test Igmp Statistics.
    @Test
    public void testIgmpStatistics() throws InterruptedException {
        igmpManager.networkConfig = new TestNetworkConfigRegistry(false);
        igmpManager.activate();
        //IGMPv3 Join
        Ethernet igmpv3MembershipReportPkt = IgmpSender.getInstance().buildIgmpV3Join(GROUP_IP, SOURCE_IP_OF_A);
        sendPacket(igmpv3MembershipReportPkt, true);
        synchronized (savedPackets) {
            savedPackets.wait(WAIT_TIMEOUT);
        }
        //Leave
        Ethernet igmpv3LeavePkt = IgmpSender.getInstance().buildIgmpV3Leave(GROUP_IP, SOURCE_IP_OF_A);
        sendPacket(igmpv3LeavePkt, true);
        synchronized (savedPackets) {
            savedPackets.wait(WAIT_TIMEOUT);
        }


        assertAfter(WAIT_TIMEOUT, WAIT_TIMEOUT * 2, () ->
            assertEquals((long) 2, igmpStatisticsManager.getIgmpStats().getTotalMsgReceived().longValue()));
        assertEquals((long) 1, igmpStatisticsManager.getIgmpStats().getIgmpJoinReq().longValue());
        assertEquals((long) 2, igmpStatisticsManager.getIgmpStats().getIgmpv3MembershipReport().longValue());
        assertEquals((long) 1, igmpStatisticsManager.getIgmpStats().getIgmpSuccessJoinRejoinReq().longValue());
        assertEquals((long) 1, igmpStatisticsManager.getIgmpStats().getUnconfiguredGroupCounter().longValue());
        assertEquals((long) 2, igmpStatisticsManager.getIgmpStats().getValidIgmpPacketCounter().longValue());
        assertEquals((long) 1, igmpStatisticsManager.getIgmpStats().getIgmpChannelJoinCounter().longValue());

        assertEquals((long) 1, igmpStatisticsManager.getIgmpStats().getIgmpLeaveReq().longValue());
        assertAfter(WAIT_TIMEOUT, WAIT_TIMEOUT * 2, () ->
        assertEquals((long) 2, igmpStatisticsManager.getIgmpStats().getIgmpMsgReceived().longValue()));

    }

    @Test
    public void testUnknownMulticastIpAddress() throws InterruptedException {
        SingleStateMachine.sendQuery = false;

        igmpManager.networkConfig = new TestNetworkConfigRegistry(false);
        igmpManager.activate();

        Ethernet firstPacket = IgmpSender.getInstance().buildIgmpV3Join(Ip4Address.valueOf("124.0.0.0"), SOURCE_IP_OF_A);
        // Sending first packet
        sendPacket(firstPacket, false);
        assertAfter(WAIT_TIMEOUT, WAIT_TIMEOUT * 2, () ->
        assertEquals((long) 1, igmpStatisticsManager.getIgmpStats().getFailJoinReqUnknownMulticastIpCounter().longValue()));
    }
}
