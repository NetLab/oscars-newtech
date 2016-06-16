package net.es.oscars;

import lombok.extern.slf4j.Slf4j;
import net.es.oscars.CoreUnitTestConfiguration;
import net.es.oscars.dto.pss.EthFixtureType;
import net.es.oscars.dto.pss.EthJunctionType;
import net.es.oscars.dto.pss.EthPipeType;
import net.es.oscars.resv.ent.RequestedVlanFixtureE;
import net.es.oscars.resv.ent.RequestedVlanJunctionE;
import net.es.oscars.resv.ent.RequestedVlanPipeE;
import net.es.oscars.servicetopo.LogicalEdge;
import net.es.oscars.servicetopo.ServiceLayerTopology;
import net.es.oscars.topo.beans.TopoEdge;
import net.es.oscars.topo.beans.TopoVertex;
import net.es.oscars.topo.beans.Topology;
import net.es.oscars.topo.dao.UrnRepository;
import net.es.oscars.topo.ent.UrnE;
import net.es.oscars.topo.enums.Layer;
import net.es.oscars.topo.enums.UrnType;
import net.es.oscars.topo.enums.VertexType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by jeremy on 6/15/16.
 */

@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(CoreUnitTestConfiguration.class)
public class ServiceLayerTopoLogicalLinkTest
{
    @Autowired
    private ServiceLayerTopology serviceLayerTopo;

    private Set<TopoVertex> ethernetTopoVertices = new HashSet<>();
    private Set<TopoVertex> mplsTopoVertices = new HashSet<>();
    private Set<TopoVertex> internalTopoVertices = new HashSet<>();

    private Set<TopoEdge> ethernetTopoEdges = new HashSet<>();
    private Set<TopoEdge> mplsTopoEdges = new HashSet<>();
    private Set<TopoEdge> internalTopoEdges = new HashSet<>();

    private RequestedVlanPipeE requestedPipe = new RequestedVlanPipeE();

    @Test
    public void verifyLogicalLinkWeights()
    {
        buildLinearTopo();
        constructLayeredTopology();
        buildLinearRequestPipe();

        Set<LogicalEdge> logicalLinks = serviceLayerTopo.getLogicalLinks();

        assert(logicalLinks.size() == 2);

        serviceLayerTopo.getLogicalLinks().stream()
                .forEach(ll -> {
                    String aURN = ll.getA().getUrn();
                    String zURN = ll.getZ().getUrn();
                    if(aURN.equals("switchA:2"))
                        assert(zURN.equals("switchE:1"));
                    else if(aURN.equals("switchE:1"))
                        assert(zURN.equals("switchA:2"));
                    else
                        assert(false);
                });

        TopoVertex srcDevice = null, dstDevice = null, srcPort = null, dstPort = null;

        for(TopoVertex v : ethernetTopoVertices)
        {
            if(v.getUrn().equals("switchA"))
                srcDevice = v;
            else if(v.getUrn().equals("switchE"))
                dstDevice = v;
            else if(v.getUrn().equals("switchA:2"))
                srcPort = v;
            else if(v.getUrn().equals("switchE:1"))
                dstPort = v;
        }

        serviceLayerTopo.buildLogicalLayerSrcNodes(srcDevice, srcPort);
        serviceLayerTopo.buildLogicalLayerDstNodes(dstDevice, dstPort);
        serviceLayerTopo.calculateLogicalLinkWeights(requestedPipe);

        logicalLinks = serviceLayerTopo.getLogicalLinks();

        assert(logicalLinks.size() == 2);

        for(LogicalEdge ll : logicalLinks)
        {
            List<TopoEdge> physicalEdges = ll.getCorrespondingTopoEdges();

            assert(ll.getA().equals(srcPort) || ll.getZ().equals(srcPort));
            assert(ll.getA().equals(dstPort) || ll.getZ().equals(dstPort));
            assert(ll.getMetric() == 400);
            assert(physicalEdges.size() == 10);

            String physicalURNs = "";
            String correctURNs;

            if(ll.getA().equals(srcPort))
            {
                assert(physicalEdges.get(0).getA().equals(srcPort));
                assert(physicalEdges.get(physicalEdges.size()-1).getZ().equals(dstPort));

                correctURNs = "switchA:2]-->[routerB:1-routerB:1]-->[routerB-routerB]-->[routerB:2-routerB:2]-->[routerC:1-routerC:1]-->[routerC-routerC]-->[routerC:2-routerC:2]-->[routerD:1-routerD:1]-->[routerD-routerD]-->[routerD:2-routerD:2]-->[switchE:1-";
            }
            else
            {
                assert(physicalEdges.get(0).getA().equals(dstPort));
                assert(physicalEdges.get(physicalEdges.size()-1).getZ().equals(srcPort));

                correctURNs = "switchE:1]-->[routerD:2-routerD:2]-->[routerD-routerD]-->[routerD:1-routerD:1]-->[routerC:2-routerC:2]-->[routerC-routerC]-->[routerC:1-routerC:1]-->[routerB:2-routerB:2]-->[routerB-routerB]-->[routerB:1-routerB:1]-->[switchA:2-";
            }

            for(TopoEdge physEdge : physicalEdges)
            {
                physicalURNs = physicalURNs + physEdge.getA().getUrn();
                physicalURNs = physicalURNs + "]-->[";
                physicalURNs = physicalURNs + physEdge.getZ().getUrn();
                physicalURNs = physicalURNs + "-";
            }

            assert(physicalURNs.equals(correctURNs));
        }

    }

    private void constructLayeredTopology()
    {
        Topology dummyEthernetTopo = new Topology();
        Topology dummyInternalTopo = new Topology();
        Topology dummyMPLSTopo = new Topology();

        dummyEthernetTopo.setLayer(Layer.ETHERNET);
        dummyEthernetTopo.setVertices(ethernetTopoVertices);
        dummyEthernetTopo.setEdges(ethernetTopoEdges);

        dummyInternalTopo.setLayer(Layer.INTERNAL);
        dummyInternalTopo.setVertices(internalTopoVertices);
        dummyInternalTopo.setEdges(internalTopoEdges);

        dummyMPLSTopo.setLayer(Layer.MPLS);
        dummyMPLSTopo.setVertices(mplsTopoVertices);
        dummyMPLSTopo.setEdges(mplsTopoEdges);

        serviceLayerTopo.setTopology(dummyEthernetTopo);
        serviceLayerTopo.setTopology(dummyInternalTopo);
        serviceLayerTopo.setTopology(dummyMPLSTopo);

        serviceLayerTopo.createMultilayerTopology();
    }

    private void buildLinearTopo()
    {
        //Devices
        TopoVertex nodeA = new TopoVertex("switchA", VertexType.SWITCH);
        TopoVertex nodeB = new TopoVertex("routerB", VertexType.ROUTER);
        TopoVertex nodeC = new TopoVertex("routerC", VertexType.ROUTER);
        TopoVertex nodeD = new TopoVertex("routerD", VertexType.ROUTER);
        TopoVertex nodeE = new TopoVertex("switchE", VertexType.SWITCH);

        //Ports
        TopoVertex portA1 = new TopoVertex("switchA:1", VertexType.PORT);
        TopoVertex portA2 = new TopoVertex("switchA:2", VertexType.PORT);
        TopoVertex portB1 = new TopoVertex("routerB:1", VertexType.PORT);
        TopoVertex portB2 = new TopoVertex("routerB:2", VertexType.PORT);
        TopoVertex portC1 = new TopoVertex("routerC:1", VertexType.PORT);
        TopoVertex portC2 = new TopoVertex("routerC:2", VertexType.PORT);
        TopoVertex portD1 = new TopoVertex("routerD:1", VertexType.PORT);
        TopoVertex portD2 = new TopoVertex("routerD:2", VertexType.PORT);
        TopoVertex portE1 = new TopoVertex("switchE:1", VertexType.PORT);
        TopoVertex portE2 = new TopoVertex("switchE:2", VertexType.PORT);

        //Internal Links
        TopoEdge edgeInt_A1_A = new TopoEdge(portA1, nodeA, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_A2_A = new TopoEdge(portA2, nodeA, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_B1_B = new TopoEdge(portB1, nodeB, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_B2_B = new TopoEdge(portB2, nodeB, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_C1_C = new TopoEdge(portC1, nodeC, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_C2_C = new TopoEdge(portC2, nodeC, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_D1_D = new TopoEdge(portD1, nodeD, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_D2_D = new TopoEdge(portD2, nodeD, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_E1_E = new TopoEdge(portE1, nodeE, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_E2_E = new TopoEdge(portE2, nodeE, 0L, Layer.INTERNAL);

        //Internal Reverse Links
        TopoEdge edgeInt_A_A1 = new TopoEdge(nodeA, portA1, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_A_A2 = new TopoEdge(nodeA, portA2, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_B_B1 = new TopoEdge(nodeB, portB1, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_B_B2 = new TopoEdge(nodeB, portB2, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_C_C1 = new TopoEdge(nodeC, portC1, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_C_C2 = new TopoEdge(nodeC, portC2, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_D_D1 = new TopoEdge(nodeD, portD1, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_D_D2 = new TopoEdge(nodeD, portD2, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_E_E1 = new TopoEdge(nodeE, portE1, 0L, Layer.INTERNAL);
        TopoEdge edgeInt_E_E2 = new TopoEdge(nodeE, portE2, 0L, Layer.INTERNAL);

        //Network Links
        TopoEdge edgeEth_A2_B1 = new TopoEdge(portA2, portB1, 100L, Layer.ETHERNET);
        TopoEdge edgeMpls_B2_C1 = new TopoEdge(portB2, portC1, 100L, Layer.MPLS);
        TopoEdge edgeMpls_C2_D1 = new TopoEdge(portC2, portD1, 100L, Layer.MPLS);
        TopoEdge edgeEth_D2_E1 = new TopoEdge(portD2, portE1, 100L, Layer.ETHERNET);
        TopoEdge edgeEth_B1_A2 = new TopoEdge(portB1, portA2, 100L, Layer.ETHERNET);
        TopoEdge edgeMpls_C1_B2 = new TopoEdge(portC1, portB2, 100L, Layer.MPLS);
        TopoEdge edgeMpls_D1_C2 = new TopoEdge(portD1, portC2, 100L, Layer.MPLS);
        TopoEdge edgeEth_E1_D2 = new TopoEdge(portE1, portD2, 100L, Layer.ETHERNET);

        ethernetTopoVertices.add(nodeA);
        ethernetTopoVertices.add(nodeE);
        ethernetTopoVertices.add(portA1);
        ethernetTopoVertices.add(portA2);
        ethernetTopoVertices.add(portE1);
        ethernetTopoVertices.add(portE2);

        mplsTopoVertices.add(nodeB);
        mplsTopoVertices.add(nodeC);
        mplsTopoVertices.add(nodeD);
        mplsTopoVertices.add(portB1);
        mplsTopoVertices.add(portB2);
        mplsTopoVertices.add(portC1);
        mplsTopoVertices.add(portC2);
        mplsTopoVertices.add(portD1);
        mplsTopoVertices.add(portD2);

        internalTopoEdges.add(edgeInt_A1_A);
        internalTopoEdges.add(edgeInt_A2_A);
        internalTopoEdges.add(edgeInt_B1_B);
        internalTopoEdges.add(edgeInt_B2_B);
        internalTopoEdges.add(edgeInt_C1_C);
        internalTopoEdges.add(edgeInt_C2_C);
        internalTopoEdges.add(edgeInt_D1_D);
        internalTopoEdges.add(edgeInt_D2_D);
        internalTopoEdges.add(edgeInt_E1_E);
        internalTopoEdges.add(edgeInt_E2_E);
        internalTopoEdges.add(edgeInt_A_A1);
        internalTopoEdges.add(edgeInt_A_A2);
        internalTopoEdges.add(edgeInt_B_B1);
        internalTopoEdges.add(edgeInt_B_B2);
        internalTopoEdges.add(edgeInt_C_C1);
        internalTopoEdges.add(edgeInt_C_C2);
        internalTopoEdges.add(edgeInt_D_D1);
        internalTopoEdges.add(edgeInt_D_D2);
        internalTopoEdges.add(edgeInt_E_E1);
        internalTopoEdges.add(edgeInt_E_E2);

        ethernetTopoEdges.add(edgeEth_A2_B1);
        ethernetTopoEdges.add(edgeEth_B1_A2);
        ethernetTopoEdges.add(edgeEth_D2_E1);
        ethernetTopoEdges.add(edgeEth_E1_D2);

        mplsTopoEdges.add(edgeMpls_B2_C1);
        mplsTopoEdges.add(edgeMpls_C1_B2);
        mplsTopoEdges.add(edgeMpls_C2_D1);
        mplsTopoEdges.add(edgeMpls_D1_C2);
    }

    private void buildLinearRequestPipe()
    {
        RequestedVlanPipeE bwPipe = new RequestedVlanPipeE();
        RequestedVlanJunctionE aJunc = new RequestedVlanJunctionE();
        RequestedVlanJunctionE zJunc = new RequestedVlanJunctionE();
        RequestedVlanFixtureE aFix = new RequestedVlanFixtureE();
        RequestedVlanFixtureE zFix = new RequestedVlanFixtureE();
        UrnE aFixURN = new UrnE();
        UrnE zFixURN = new UrnE();
        UrnE aJuncURN = new UrnE();
        UrnE zJuncURN = new UrnE();

        aFixURN.setUrn("switchA:1");
        aFixURN.setUrnType(UrnType.IFCE);

        zFixURN.setUrn("switchE:1");
        zFixURN.setUrnType(UrnType.IFCE);

        aJuncURN.setUrn("switchA");
        aJuncURN.setUrnType(UrnType.DEVICE);

        zJuncURN.setUrn("switchE:1");
        zJuncURN.setUrnType(UrnType.DEVICE);


        aFix.setPortUrn(aFixURN);
        aFix.setVlanExpression("1234");
        aFix.setFixtureType(EthFixtureType.JUNOS_IFCE);
        aFix.setInMbps(100);
        aFix.setEgMbps(100);

        zFix.setPortUrn(zFixURN);
        zFix.setVlanExpression("1234");
        zFix.setFixtureType(EthFixtureType.JUNOS_IFCE);
        zFix.setInMbps(100);
        zFix.setEgMbps(100);

        Set<RequestedVlanFixtureE> aFixes = new HashSet<>();
        Set<RequestedVlanFixtureE> zFixes = new HashSet<>();

        aFixes.add(aFix);
        zFixes.add(zFix);

        aJunc.setDeviceUrn(aJuncURN);
        aJunc.setJunctionType(EthJunctionType.JUNOS_SWITCH);
        aJunc.setFixtures(aFixes);

        zJunc.setDeviceUrn(zJuncURN);
        zJunc.setJunctionType(EthJunctionType.JUNOS_SWITCH);
        zJunc.setFixtures(zFixes);


        bwPipe.setAzMbps(20);
        bwPipe.setZaMbps(20);
        bwPipe.setAJunction(aJunc);
        bwPipe.setZJunction(zJunc);
        bwPipe.setPipeType(EthPipeType.REQUESTED);

        requestedPipe = bwPipe;
    }
}
