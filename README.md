selenium-zk-grid
================

/registrationRequests - registration request queue, (String) nodeId
/newSessionRequests - new session request queue, (Capabilities) desiredCapabilities

/nodes/{nodeId}/heartbeat - node aliveness mark, timestamp of last ping
/nodes/{nodeId}/barrier - registration confirmation barrier

/nodes/{nodeId}/slots/{slotId}/command
/nodes/{nodeId}/slots/{slotId}/response
/nodes/{nodeId}/slots/{slotId}/barrier

/nodes/{nodeId}/slots/{slotId}/state

/clients/{clientId}/slot - allocated slot
/clients/{clientId}/barrier - slot allocation barrier