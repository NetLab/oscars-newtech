const vis = require('../../../node_modules/vis/dist/vis');
const client = require('./client');

let resv_viz_name = "reservation_viz";

function make_network(nodes, edges, container, options, name) {
    // datasource{ nodes: vis.DataSet(nodes), edges -> vis.DataSet(edges) }

    let nodeDataset = new vis.DataSet(nodes);
    let edgeDataset = new vis.DataSet(edges);
    let datasource = {
        nodes: nodeDataset,
        edges: edgeDataset
    };

    return make_network_with_datasource(datasource, container, options, name);
}

function make_network_with_datasource(datasource, container, options, name) {
    // datasource{ nodes: vis.DataSet(nodes), edges -> vis.DataSet(edges) }


    let network = new vis.Network(container, datasource, options);

    let result = {};
    result.network = network;
    result.datasource = datasource;

    attach_handlers(network, datasource, name);
    return result;
}

function attach_handlers(vis_js_network, vis_js_datasets, name) {

    let selected_node_ids = {};
    vis_js_network.on('dragEnd', function (params) {
        selected_node_ids.name = [];
        for (let i = 0; i < params.nodes.length; i++) {
            let nodeId = params.nodes[i];

            selected_node_ids.name.push(nodeId);
            if (vis_js_network.isCluster(nodeId) == true) {
                vis_js_network.clustering.updateClusteredNode(nodeId, {fixed: {x: true, y: true}});
            } else {
                vis_js_datasets.nodes.update({id: nodeId, fixed: {x: true, y: true}});
            }
        }
    });

    vis_js_network.on('dragStart', function (params) {
        let draggedPlain = false;
        selected_node_ids.name = [];
        for (let i = 0; i < params.nodes.length; i++) {
            let nodeId = params.nodes[i];
            selected_node_ids.name.push(nodeId);

            if (vis_js_network.isCluster(nodeId) == true) {
                vis_js_network.clustering.updateClusteredNode(nodeId, {fixed: {x: false, y: false}});
            } else {
                vis_js_datasets.nodes.update({id: nodeId, fixed: {x: false, y: false}});
                draggedPlain = true;
            }
        }

        let is_resv = false;
        if (name == resv_viz_name) {
            is_resv = true;
        }

    });

    vis_js_network.on("click", function (params) {
        let clickedNode = false;
        let clickedEdge = false;
        let clickedPlain = false;
        let edgeId = "";
        let nodeId = "";
        selected_node_ids.name = [];
        let i;

        for (i = 0; i < params.nodes.length; i++) {
            clickedNode = true;
            nodeId = params.nodes[i];

            if (!vis_js_network.isCluster(nodeId) == true) {
                clickedPlain = true;
            }
            selected_node_ids.name.push(nodeId);
        }

        let is_resv = false;
        if (name == resv_viz_name) {
            is_resv = true;
        }

        if (!clickedNode) {
            for (i = 0; i < params.edges.length; i++) {
                clickedEdge = true;
                edgeId = params.edges[i];
                edgeId = vis_js_network.clustering.getBaseEdge(edgeId);
                console.log("edge selected: " + edgeId);
            }
        }
        trigger_form_changes(is_resv, clickedEdge, clickedNode, clickedPlain, nodeId, edgeId);
    });
}

function drawPathOnNetwork(vizNetwork, allAzPaths)
{
    highlight_devices(vizNetwork.datasource, Object.keys(vizNetwork.datasource.nodes._data), true, "white");
    highlight_links(vizNetwork.datasource, Object.keys(vizNetwork.datasource.edges._data), true, "blue");

    let eachAzPath = allAzPaths.split(";");
    let nodesToReserve = [];
    let linksToReserve = [];

    for(let i = 0; i < eachAzPath.length-1; i++)
    {
        console.log("Path: " + eachAzPath[i]);

        let eachAzNode = eachAzPath[i].split(",");
        let prevNode = "";
        let prevNodeIsDevice = false;
        for(let j = 0; j < eachAzNode.length-1; j++)
        {
            let nextNode = eachAzNode[j];

            if(nextNode === prevNode)
                continue;



            let portNodes = nextNode.split(":");
            let nextNodeIsDevice = false;
            nextNodeIsDevice = portNodes.length <= 1;

            if(nextNodeIsDevice)
            {
                nodesToReserve.push(nextNode);
            }

            if(!prevNodeIsDevice && !nextNodeIsDevice && j > 0)
            {
                let linkName = prevNode + " -- " + nextNode;
                let reverseLinkName = nextNode + " -- " + prevNode;     // Not all links are bidirectional in the viz. Won't be colored properly.
                linksToReserve.push(linkName);
                linksToReserve.push(reverseLinkName);
            }

            prevNode = eachAzNode[j];
            let prevPort = prevNode.split(":");
            prevNodeIsDevice = prevPort.length <= 1;
        }
    }

    vizNetwork.network.unselectAll();

    highlight_devices(vizNetwork.datasource, nodesToReserve, true, "green");
    highlight_links(vizNetwork.datasource, linksToReserve, true, "green");

    //let highlightedNodes = nodesToReserve;
    //let highlightedEdges = linksToReserve;
}

function drawFailedLinksOnNetwork(vizNetwork, resRequest)
{
    console.log("Updating Topology with bandwidth-restricted links");
    highlight_devices(vizNetwork.datasource, Object.keys(vizNetwork.datasource.nodes._data), true, "white");
    highlight_links(vizNetwork.datasource, Object.keys(vizNetwork.datasource.edges._data), true, "blue");

    // Compute Maximum requested B/W from requested pipes //
    let maxPipeBW = 0;

    let pipeIds = Object.keys(resRequest.pipes);
    for(let p = 0; p < pipeIds.length; p++)
    {
        let oneBW = parseInt(resRequest.pipes[pipeIds[p]].bw);

        if(oneBW > maxPipeBW)
            maxPipeBW = oneBW;
    }

    console.log("Max pipe B/W: " + maxPipeBW);

    // Identify the network ports
    client.loadJSON({method: "GET", url:"/viz/listPorts"}).then(
        (portsResponse) => {
            let netPorts = JSON.parse(portsResponse);
            client.submitReservation("/resv/topo/bwAvailAllPorts/", resRequest).then(
                (response) => {
                    let parsedResponse = JSON.parse(response);
                    let fullPortMap = parsedResponse["bwAvailabilityMap"];
                    let problemPorts = [];
                    let insufficientNodes = [];
                    let insufficientEdges = [];

                    let junctionKeys = Object.keys(resRequest["junctions"]);
                    for(let p = 0; p < netPorts.length; p++)
                    {
                        let onePort = netPorts[p];              // 1. Get b/w for each port from Map.
                        let inBW = fullPortMap[onePort][0];
                        let egBW = fullPortMap[onePort][1];

                        let minBW = inBW;                       // 2. Take minimum of ingress and egress.

                        if(egBW < minBW)
                            minBW = egBW;

                        if(minBW < maxPipeBW)                  // 3. If minimum >= b/w requested, continue.
                        {
                            problemPorts.push(onePort);         // 4. Otherwise, track problematic ports.

                            if(insufficientNodes.length === junctionKeys.length)
                                continue;

                            for(let j = 0; j < junctionKeys.length; j++)
                            {
                                // Need to color nodes if fixture bandwidth is insufficient
                                if(onePort in resRequest["junctions"][junctionKeys[j]]["fixtures"])
                                    insufficientNodes.push(junctionKeys[j]);
                            }
                        }
                    }

                    console.log("Insufficient Nodes: " + insufficientNodes);

                    // 5. Find all links terminating at each problematic port.
                    for(let p = 0; p < problemPorts.length; p++)
                    {
                        let badPort = problemPorts[p];

                        let edgeKeys = Object.keys(vizNetwork.datasource.edges._data);
                        for(let l = 0; l < edgeKeys.length; l++)
                        {
                            let key = edgeKeys[l];
                            let oneLink = vizNetwork.datasource.edges._data[key];
                            let endPoints = oneLink.id.split(" -- ");

                            if(badPort === endPoints[0] || badPort === endPoints[1])
                            {
                                insufficientEdges.push(oneLink.id);
                            }
                        }
                    }



                    // 6. Color all problematic links and nodes red.
                    vizNetwork.network.unselectAll();
                    highlight_devices(vizNetwork.datasource, insufficientNodes, true, "red");
                    highlight_links(vizNetwork.datasource, insufficientEdges, true, "red");

                    console.log("Failed links drawn");
                }
            );
        }
    );



}



function highlight_devices(network, deviceIDs, isSelected, color)
{
    let newColor = color;
    let normalNodeColor = "white";

    if($.isEmptyObject(deviceIDs))
        return;

    for(let d = 0; d < deviceIDs.length; d++)
    {
        if(isSelected)
            network.nodes.update([{id: deviceIDs[d], color: {background: newColor}}]);
        else
            network.nodes.update([{id: deviceIDs[d], color: {background: normalNodeColor}}]);
    }
}

function highlight_links(network, linkIDs, isSelected, color)
{
    let newColor = color;
    let normalEdgeColor = "#2F7FED";

    if($.isEmptyObject(linkIDs))
        return;

    for(let l = 0; l < linkIDs.length; l++)
    {
        if(isSelected)
            network.edges.update([{id: linkIDs[l], color: {color: newColor}}]);
        else
            network.edges.update([{id: linkIDs[l], color: {color: normalEdgeColor}}]);
    }
}

function trigger_form_changes(is_resv, selected_an_edge, selected_a_node, is_selected_node_plain, nodeId, edgeId)
{
    //TODO: Implement some actions when parts of reservation viz are selected
    ;
}

module.exports = {make_network, make_network_with_datasource, drawPathOnNetwork, drawFailedLinksOnNetwork};